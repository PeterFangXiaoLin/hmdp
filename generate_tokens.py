"""
批量登录脚本：为数据库中的1000+用户生成 token 并保存到 tokens.txt
原理：
  1. 从 MySQL tb_user 表读取所有手机号
  2. 直接向 Redis 写入固定验证码 "123456"（绕过短信发送）
  3. 调用登录接口 POST /user/login 获取 token
  4. 将 token 逐行写入 tokens.txt，供 JMeter 读取

依赖安装：
  pip install pymysql redis requests
"""

import pymysql
import redis
import requests
import time

# ──────────────── 配置区 ────────────────
MYSQL_HOST     = "127.0.0.1"
MYSQL_PORT     = 3306
MYSQL_USER     = "root"
MYSQL_PASSWORD = "123456"
MYSQL_DB       = "hmdp"

REDIS_HOST     = "localhost"
REDIS_PORT     = 6379
REDIS_DB       = 0
REDIS_PASSWORD = None          # 如果 Redis 有密码，填到这里

API_BASE_URL   = "http://127.0.0.1:8081"   # 确保 Spring Boot 应用已启动
FAKE_CODE      = "123456"                  # 写入 Redis 的固定验证码
CODE_TTL       = 120                       # 验证码 TTL（秒），与项目一致

OUTPUT_FILE    = "tokens.txt"
# ────────────────────────────────────────


def get_phones_from_db():
    """从 tb_user 表读取所有手机号"""
    conn = pymysql.connect(
        host=MYSQL_HOST, port=MYSQL_PORT,
        user=MYSQL_USER, password=MYSQL_PASSWORD,
        database=MYSQL_DB, charset="utf8mb4"
    )
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT phone FROM tb_user WHERE phone IS NOT NULL AND phone != ''")
            rows = cur.fetchall()
            phones = [row[0] for row in rows]
            print(f"[MySQL] 共读取到 {len(phones)} 个手机号")
            return phones
    finally:
        conn.close()


def inject_codes_to_redis(phones: list, r: redis.Redis):
    """批量向 Redis 写入固定验证码，key 格式：login:code:{phone}"""
    pipe = r.pipeline()
    for phone in phones:
        key = f"login:code:{phone}"
        pipe.setex(key, CODE_TTL, FAKE_CODE)
    pipe.execute()
    print(f"[Redis] 已为 {len(phones)} 个手机号注入验证码 '{FAKE_CODE}'，TTL={CODE_TTL}s")


def login_and_get_token(phone: str, session: requests.Session) -> str | None:
    """调用登录接口，返回 token 字符串，失败返回 None"""
    url = f"{API_BASE_URL}/user/login"
    payload = {"phone": phone, "code": FAKE_CODE}
    try:
        resp = session.post(url, json=payload, timeout=10)
        if resp.status_code == 200:
            body = resp.json()
            if body.get("success") and body.get("data"):
                return body["data"]
            else:
                print(f"  [WARN] {phone} 登录失败: {body.get('errorMsg')}")
        else:
            print(f"  [WARN] {phone} HTTP {resp.status_code}")
    except Exception as e:
        print(f"  [ERROR] {phone} 请求异常: {e}")
    return None


def main():
    # 1. 连接 Redis
    r = redis.Redis(
        host=REDIS_HOST, port=REDIS_PORT, db=REDIS_DB,
        password=REDIS_PASSWORD, decode_responses=True
    )
    r.ping()
    print(f"[Redis] 连接成功 {REDIS_HOST}:{REDIS_PORT}")

    # 2. 读取手机号
    phones = get_phones_from_db()
    if not phones:
        print("[ERROR] 未读取到任何手机号，请检查数据库连接或表数据")
        return

    # 3. 批量注入验证码
    inject_codes_to_redis(phones, r)

    # 4. 批量登录，收集 token
    tokens = []
    failed = []
    http_session = requests.Session()

    print(f"\n[Login] 开始批量登录，共 {len(phones)} 个用户...")
    start = time.time()

    for i, phone in enumerate(phones, 1):
        token = login_and_get_token(phone, http_session)
        if token:
            tokens.append(token)
        else:
            failed.append(phone)

        # 每 100 个打印一次进度
        if i % 100 == 0:
            elapsed = time.time() - start
            print(f"  进度: {i}/{len(phones)}，成功 {len(tokens)}，耗时 {elapsed:.1f}s")

    elapsed = time.time() - start
    print(f"\n[完成] 总耗时 {elapsed:.1f}s，成功 {len(tokens)}，失败 {len(failed)}")

    # 5. 保存 token 到文件（每行一个 token）
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        for token in tokens:
            f.write(token + "\n")
    print(f"[输出] 已将 {len(tokens)} 个 token 写入 {OUTPUT_FILE}")

    if failed:
        fail_file = "failed_phones.txt"
        with open(fail_file, "w", encoding="utf-8") as f:
            f.write("\n".join(failed))
        print(f"[输出] {len(failed)} 个失败手机号已写入 {fail_file}")


if __name__ == "__main__":
    main()
