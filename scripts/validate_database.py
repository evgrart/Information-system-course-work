"""Проверка целостности, конкуренции и полного цикла создания/удаления БД.

Запуск на helios: python3.11 scripts/validate_database.py --schema s465826
Требуются только Python и psql. Рабочий набор lf_* не изменяется.
"""
import argparse
import concurrent.futures
from pathlib import Path
import subprocess
import tempfile
import os
import time

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--schema',default=os.environ.get('DB_SCHEMA','s465826'))
args=parser.parse_args()
if not args.schema.replace('_','').isalnum():
    parser.error('Недопустимое имя схемы')
env=os.environ.copy()
env.setdefault('PGHOST','pg'); env.setdefault('PGDATABASE','studs')
base=['psql','-X','-w','-v','ON_ERROR_STOP=1','-v',f'schema={args.schema}']

def sql(text,check=True):
    prefix=f'SET search_path TO "{args.schema}", pg_catalog;\n'
    result=subprocess.run(base+['-At'],input=prefix+text,text=True,capture_output=True,env=env,timeout=45)
    if check and result.returncode:
        raise RuntimeError(result.stderr.strip())
    return result

def file(path):
    result=subprocess.run(base+['-f',str(path)],text=True,capture_output=True,env=env,timeout=90)
    if result.returncode: raise RuntimeError(result.stderr.strip())
    return result.stdout

def race(a,b):
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        first=pool.submit(sql,a,False)
        time.sleep(0.15)
        second=pool.submit(sql,b,False)
        return first.result(),second.result()

fingerprint_sql=f"""SELECT md5(string_agg(c.oid::text||':'||c.relname||':'||c.relkind::text,'|' ORDER BY c.oid))
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='{args.schema}' AND c.relname NOT LIKE 'lfcheck\\_%' ESCAPE '\\';"""
baseline=sql(fingerprint_sql).stdout.strip()
if sql(f"SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='{args.schema}' AND c.relname LIKE 'lfcheck\\_%' ESCAPE '\\';").stdout.strip().splitlines()[-1]!='0':
    raise RuntimeError('Найдены объекты lfcheck_*. Их удаление автоматически не выполняется.')

created=False
with tempfile.TemporaryDirectory(prefix='poteryashki-validation-') as tmp:
    folder=Path(tmp)
    for source in (ROOT/'database').glob('*.sql'):
        text=source.read_text(encoding='utf-8').replace('lf\\_','lfcheck\\_').replace('lf_','lfcheck_')
        (folder/source.name).write_text(text,encoding='utf-8')
    try:
        file(folder/'create.sql'); created=True
        file(folder/'seed.sql')
        result=file(folder/'test.sql')
        if '50' not in result or 'ROLLBACK' not in result: raise RuntimeError('Нет результата проверок целостности')
        print('PASS: 50 проверок целостности в изолированном наборе lfcheck_*')
        a,b=race("BEGIN; SELECT 1 FROM lfcheck_listings WHERE id=1 FOR UPDATE; SELECT pg_sleep(0.7); SELECT lfcheck_reserve_found(1,3); COMMIT;",
                 "BEGIN; SELECT lfcheck_reserve_found(2,3); COMMIT;")
        if a.returncode or not b.returncode or 'Находка недоступна' not in b.stderr:
            raise RuntimeError(f'Конкурирующие резервы: {a.stderr} / {b.stderr}')
        if sql("SELECT count(*) FROM lfcheck_claims WHERE listing_id=1 AND state='accepted';").stdout.strip().splitlines()[-1]!='1':
            raise RuntimeError('Созданы два резерва')
        print('PASS: два соединения, один принятый резерв')
        a,b=race("BEGIN; SELECT 1 FROM lfcheck_listings WHERE id=2 FOR UPDATE; SELECT pg_sleep(0.7); SELECT lfcheck_place_bid(1,4,1200,'40000000-0000-0000-0000-000000000001'); COMMIT;",
                 "BEGIN; SELECT lfcheck_place_bid(1,5,1200,'40000000-0000-0000-0000-000000000002'); COMMIT;")
        if a.returncode or not b.returncode or 'Ставка ниже' not in b.stderr:
            raise RuntimeError(f'Конкурирующие ставки: {a.stderr} / {b.stderr}')
        print('PASS: две одинаковые конкурентные ставки, принята одна')
        sql("INSERT INTO lfcheck_payment_orders(id,user_id,tariff_id,request_key,amount,duration_days) VALUES (50,5,1,'40000000-0000-0000-0000-000000000050',149,30);")
        a,b=race("BEGIN; SELECT 1 FROM lfcheck_users WHERE id=5 FOR UPDATE; SELECT pg_sleep(0.7); SELECT lfcheck_activate_subscription(50,'race-payment',149); COMMIT;",
                 "BEGIN; SELECT lfcheck_activate_subscription(50,'race-payment',149); COMMIT;")
        if a.returncode or b.returncode:
            raise RuntimeError(f'Конкурирующая оплата: {a.stderr} / {b.stderr}')
        if sql("SELECT count(*) FROM lfcheck_subscriptions WHERE payment_order_id=50;").stdout.strip().splitlines()[-1]!='1':
            raise RuntimeError('Дублирование оплаченного периода')
        print('PASS: повтор оплаты из двух соединений, один период подписки')
        # Короткий аукцион позволяет проверить закрытие без изменения часов сервера.
        sql("UPDATE lfcheck_auction_permissions SET state='approved',moderator_id=1,reviewed_at=clock_timestamp() WHERE id=2; INSERT INTO lfcheck_auctions(id,listing_id,seller_id,permission_id,starts_at,ends_at,start_price,bid_step,state) VALUES (10,6,3,2,clock_timestamp(),clock_timestamp()+interval '2 seconds',100,10,'active'); SELECT lfcheck_place_bid(10,4,100,'40000000-0000-0000-0000-000000000010');")
        time.sleep(2.1)
        sql("CALL lfcheck_close_due_auctions();")
        if sql("SELECT state||':'||(winner_bid_id IS NOT NULL)::text FROM lfcheck_auctions WHERE id=10;").stdout.strip().splitlines()[-1]!='finished:true':
            raise RuntimeError('Не определён победитель')
        sql("SELECT lfcheck_finalize_auction(10);")
        print('PASS: пакетное завершение и повторное получение победителя')
    finally:
        if created:
            file(folder/'drop.sql')
            print('PASS: удалены только объекты проверочного набора lfcheck_*')
    if sql(fingerprint_sql).stdout.strip()!=baseline:
        raise RuntimeError('Изменился состав внешних объектов схемы')
    print('PASS: состав объектов основной курсовой и лабораторной сохранён')
