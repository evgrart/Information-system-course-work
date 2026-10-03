"""ER-схемы и словарь отношений из каталога развёрнутой PostgreSQL."""
from pathlib import Path
import sys
sys.stdout.reconfigure(encoding='utf-8')
import json,re
import build_report as report

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/part2/model'
TABLES=json.loads((OUT/'catalog.json').read_text(encoding='utf-8'))
BY_NAME={t['name']:t for t in TABLES}
LABELS={
 'users':'Пользователь','roles':'Роль','user_roles':'Назначение роли',
 'refresh_tokens':'Токен обновления','verifications':'Проверка профиля',
 'verification_tokens':'Одноразовый токен',
 'tariffs':'Тариф','payment_orders':'Заказ оплаты','payment_events':'Событие оплаты',
 'subscriptions':'Период подписки','organizations':'Организация','locations':'Место',
 'categories':'Категория','listings':'Объявление','listing_images':'Изображение',
 'private_attributes':'Контрольный признак','claims':'Заявка владельца',
 'conversations':'Диалог','messages':'Сообщение','transfers':'Передача',
 'complaints':'Жалоба','auction_permissions':'Допуск к продаже','auctions':'Аукцион',
 'bids':'Ставка','notifications':'Уведомление','outbox_events':'Событие доставки','audit_entries':'Запись аудита'}

def columns_key(t,kind):
    result=set()
    for c in t['constraints'] or []:
        if c['type']==kind:
            match=re.search(r'\(([^)]+)\)',c['definition'])
            if match:result.update(v.strip() for v in match[1].split(','))
    return result

def short_type(s):
    return s.replace('character varying','varchar').replace('timestamp with time zone','timestamptz').replace('character(','char(')

# Полная редактируемая даталогическая модель (Mermaid ER).
lines=['erDiagram']
relationships=[]
for t in TABLES:
    pk,fk=columns_key(t,'p'),columns_key(t,'f')
    lines.append(f"    {t['name']} {{")
    for c in t['columns']:
        typ=re.sub(r'[^a-zA-Z0-9_]','_',short_type(c['type'])).strip('_')
        key=', '.join(k for k,fields in [('PK',pk),('FK',fk)] if c['name'] in fields)
        lines.append(f"        {typ} {c['name']} {key}".rstrip())
    lines.append('    }')
    for con in t['constraints'] or []:
        if con['type']!='f':continue
        match=re.search(r'FOREIGN KEY \(([^)]+)\) REFERENCES ([^(]+)\(',con['definition'])
        fields=[v.strip() for v in match[1].split(',')]; target=match[2].strip()
        if target.startswith('s465826.'):target=target.split('.',1)[1]
        optional=any(c['nullable'] for c in t['columns'] if c['name'] in fields)
        unique=any(con2['type'] in ('u','p') and set(re.search(r'\(([^)]+)\)',con2['definition']).group(1).split(', '))<=set(fields)
                   for con2 in t['constraints'] or [])
        relationships.append((target,t['name'],','.join(fields),optional,unique))
        lines.append(f'    {target} '+('|o' if optional else '||')+'--'+('o|' if unique else 'o{')+f' {t["name"]} : "{",".join(fields)}"')
(OUT/'logical.mmd').write_text('\n'.join(lines)+'\n',encoding='utf-8')
# Концептуальная схема: предметные сущности без технических типов атрибутов.
concept=['erDiagram']
for parent,child,fields,optional,unique in relationships:
    concept.append(f'    {parent[3:]} '+('|o' if optional else '||')+'--'+('o|' if unique else 'o{')+f' {child[3:]} : "{fields}"')
(OUT/'er.mmd').write_text('\n'.join(concept)+'\n',encoding='utf-8')

report.DIAGRAMS=OUT
groups=[
 ('01_accounts','Аккаунты и подписка',
  ['roles','user_roles','users','verifications','refresh_tokens','subscriptions','tariffs','payment_orders','payment_events'],
  [('roles','user_roles','1','0..N'),('users','user_roles','1','0..N'),('users','refresh_tokens','1','0..N'),
   ('users','subscriptions','1','0..N'),('users','verifications','1','0..N'),('tariffs','payment_orders','1','0..N'),
   ('payment_orders','payment_events','1','0..N'),('payment_orders','subscriptions','1','0..1')]),
 ('02_listings','Объявления и справочники',
  ['organizations','locations','categories','users','listings','listing_images',None,'private_attributes',None],
  [('organizations','locations','0..1','0..N'),('locations','listings','1','0..N'),('categories','listings','1','0..N'),
   ('users','listings','1','0..N'),('listings','listing_images','1','0..5'),('listings','private_attributes','1','0..N')]),
 ('03_returns','Возврат и обращения',
  ['users','listings','complaints','messages','claims','transfers',None,'conversations',None],
  [('listings','claims','1','0..N'),('users','claims','1','0..N'),('claims','transfers','1','0..1'),
   ('claims','conversations','1','0..1'),('conversations','messages','1','0..N'),('listings','complaints','0..1','0..N')]),
 ('04_auctions','Допуск к продаже и аукцион',
  ['categories','listings','users',None,'auction_permissions',None,'bids','auctions',None],
  [('categories','listings','1','0..N'),('users','auction_permissions','1','0..N'),('listings','auction_permissions','1','0..N'),
   ('auction_permissions','auctions','1','0..N'),('auctions','bids','1','0..N'),('users','bids','1','0..N')]),
 ('05_events','Уведомления, доставка и аудит',
  [None,'users',None,'notifications','audit_entries','outbox_events'],
  [('users','notifications','1','0..N'),('users','audit_entries','0..1','0..N')]),
 ('06_tokens','Верификация и восстановление доступа',
  [None,'users',None,'verifications','verification_tokens','refresh_tokens'],
  [('users','verifications','1','0..N'),('users','verification_tokens','1','0..N'),('users','refresh_tokens','1','0..N')])]

for name,title,names,edges in groups:
    d=report.Drawing(name,1260,940,title)
    coords={n:(220+(i%3)*410,200+(i//3)*270) for i,n in enumerate(names) if n}
    for a,b,card_a,card_b in edges:
        ax,ay=coords[a];bx,by=coords[b]
        if ay==by:
            sign=1 if bx>ax else -1;start=(ax+sign*175,ay);end=(bx-sign*175,by)
            pts=[start,end]
        elif ax==bx:
            sign=1 if by>ay else -1;start=(ax,ay+sign*78);end=(bx,by-sign*78);pts=[start,end]
        else:
            sign=1 if by>ay else -1; start=(ax,ay+sign*78); end=(bx,by-sign*78)
            middle=(ay+by)/2;pts=[start,(ax,middle),(bx,middle),end]
        d.line(pts)
        sx,sy=start;ex,ey=end
        if ay==by:
            sign=1 if bx>ax else -1
            d.text(sx+sign*20,sy-23,card_a,20)
            d.text(ex-sign*20,ey+23,card_b,20)
        else:
            sign=1 if by>ay else -1
            d.text(sx+28,sy+sign*20,card_a,20)
            d.text(ex+28,ey-sign*20,card_b,20)
    for n,(x,y) in coords.items():
        d.box(x-175,y-78,x+175,y+78,fill='#f4f6f8')
        d.text(x,y-37,LABELS[n],26,bold=True)
        d.text(x,y, 'lf_'+n,23)
        keys=columns_key(BY_NAME['lf_'+n],'p')
        keytext='PK: '+', '.join(sorted(keys))
        if len(keytext)>27:keytext=keytext.replace(', ',',\n')
        d.text(x,y+40,keytext,20)
    d.text(630,900,'1 — один; 0..1 — необязательный; 0..N — множество',25)
    d.save()

dictionary=['### 8.2. Словарь отношений']
for i,t in enumerate(TABLES,1):
    n=t['name'];pk,fk=columns_key(t,'p'),columns_key(t,'f')
    dictionary.extend(['',f'#### {i}. {LABELS[n[3:]]}: {n}','',
        '| Поле | Тип PostgreSQL | NULL | Ключ |','| --- | --- | --- | --- |'])
    for c in t['columns']:
        key=', '.join(k for k,fields in [('PK',pk),('FK',fk)] if c['name'] in fields) or '—'
        dictionary.append(f"| {c['name']} | {short_type(c['type'])} | {'Да' if c['nullable'] else 'Нет'} | {key} |")
    for con in t['constraints'] or []:
        if con['type'] in ('p','f','u'):
            dictionary.extend(['',con['definition']+'.'])
(OUT/'dictionary.md').write_text('\n'.join(dictionary)+'\n',encoding='utf-8')
print(f'{len(TABLES)} отношений, {len(relationships)} внешних ключей, 6 схем, 2 Mermaid-модели.')
