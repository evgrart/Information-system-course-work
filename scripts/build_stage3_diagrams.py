"""Editable class diagrams of the implemented Java layers."""
from pathlib import Path
import build_report as graphics
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/part3/uml';OUT.mkdir(parents=True,exist_ok=True)
graphics.DIAGRAMS=OUT

def box(d,x,y,w,name,methods,stereotype='service',height=200):
    d.box(x,y,x+w,y+height,fill='#edf3f8' if stereotype=='service' else 'white')
    d.text(x+w/2,y+25,'«'+stereotype+'»',20)
    d.text(x+w/2,y+57,name,25,bold=True)
    d.line([(x,y+82),(x+w,y+82)])
    for i,method in enumerate(methods):d.text(x+w/2,y+110+i*32,method,22)
    return (x+w/2,y+height,x+w/2,y)

def source(name,body):
    (OUT/(name+'.puml')).write_text('@startuml\n!pragma layout smetana\nskinparam dpi 130\nskinparam shadowing false\nskinparam defaultFontName Arial\nskinparam classAttributeIconSize 0\n'+body+'\n@enduml\n',encoding='utf-8')

def overview():
    name='01_architecture';d=graphics.Drawing(name,1800,1100,'Общая диаграмма классов реализованных слоёв')
    services=[('AccountService',['+ register(): Registration','+ authenticate(): Identity']),
              ('SubscriptionService',['+ createOrder(): long','+ payDemo(): long']),
              ('ListingService',['+ create(): long','+ search(): List']),
              ('ReturnService',['+ reserve(): long','+ confirm(): boolean']),
              ('AuctionService',['+ bid(): long','+ closeDue(): void']),
              ('AdministrationService',['+ block(): void','+ audit(): List'])]
    for i,(cls,methods) in enumerate(services):box(d,20+i*297,110,277,cls,methods,height=180)
    # Separate horizontal channels for shared service dependencies.
    for i in range(6):
        x=158+i*297;d.line([(x,290),(x,335),(850,335),(850,410)],dashed=True,arrow=True)
    box(d,650,410,400,'AccessPolicy',['+ actor(user, paid): void','+ moderator(user): void','+ audit(user): void'],height=220)
    for x,name2,methods in [(40,'SqlStore',['+ update(sql, args): int','+ id(sql, args): Long','+ rows(sql, args): List']),
                            (700,'DatabaseFunctions',['+ reserve(), confirm(), bid()','+ activateSubscription()','+ search(), closeDue()']),
                            (1360,'UserRepository',['+ findById(): Optional<User>','+ findByEmail(): Optional<User>'])]:
        box(d,x,730,400,name2,methods,'repository',height=210)
    d.line([(750,630),(750,675),(240,675),(240,730)],dashed=True,arrow=True)
    d.line([(950,630),(950,675),(900,675),(900,730)],dashed=True,arrow=True)
    d.text(900,1010,'Все сервисы используют AccessPolicy; JDBC и JPA работают с одним DataSource и JpaTransactionManager.',25)
    d.text(900,1050,'ListingRepository и OutboxService показаны на детальных диаграммах. Entity: 27 таблиц этапа 2.',24)
    d.save()
    body='\n'.join('class '+cls+' <<service>> {\n'+ '\n'.join(m.replace(': ', ' : ') for m in methods)+'\n}' for cls,methods in services)
    body+='\nclass AccessPolicy <<service>>\nclass SqlStore <<repository>>\nclass DatabaseFunctions <<repository>>\ninterface UserRepository\ninterface ListingRepository\nclass OutboxService <<service>>\n'
    for cls,_ in services:body+='\n'+cls+' ..> AccessPolicy\n'+cls+' ..> SqlStore'
    for cls in ['AccountService','SubscriptionService','ListingService','ReturnService','AuctionService']:body+='\n'+cls+' ..> DatabaseFunctions'
    body+='\nAccountService ..> UserRepository\nListingService ..> ListingRepository\nOutboxService ..> SqlStore\nOutboxService ..> AccessPolicy\nAccessPolicy ..> DatabaseFunctions\nAccessPolicy ..> SqlStore'
    body+='\nAccountService -[hidden]down-> SubscriptionService\nSubscriptionService -[hidden]down-> ListingService\nReturnService -[hidden]down-> AuctionService\nAuctionService -[hidden]down-> AdministrationService'
    source(name,body)
    from render_stage3_uml import render
    render(name)

def persistence():
    name='02_persistence';d=graphics.Drawing(name,1500,1100,'Уровень хранения: JPA и функции PostgreSQL')
    box(d,40,110,620,'DatabaseFunctions',['- jdbc: JdbcTemplate','- entities: EntityManager','+ reserve(claim, finder): long'],'repository',height=215)
    box(d,840,110,620,'SqlStore',['- jdbc: JdbcTemplate','- names: SqlNames','+ one(sql, args): Map'],'repository',height=215)
    box(d,70,500,540,'UserRepository',['+ findByEmail(): Optional<User>'],'interface',height=155)
    box(d,880,500,540,'ListingRepository',['+ findById(): Optional<Listing>'],'interface',height=155)
    box(d,70,800,540,'User',['- id: Long','- passwordHash: String','- tokenVersion: Integer'],'immutable entity',height=210)
    box(d,880,800,540,'Listing',['- id: Long','- authorId: Long','- state: String'],'immutable entity',height=210)
    d.line([(610,900),(880,900)])
    d.text(750,872,'1 — 0..*',24)
    d.box(600,385,900,450,'DataSource',fill='#edf3f8')
    d.line([(350,325),(350,418),(600,418)],dashed=True,arrow=True)
    d.line([(1150,325),(1150,418),(900,418)],dashed=True,arrow=True)
    d.line([(340,655),(340,800)],dashed=True,arrow=True)
    d.line([(1150,655),(1150,800)],dashed=True,arrow=True)
    d.text(750,720,'JPA: чтение; JDBC: CRUD и вызов PL/pgSQL; DDL: validate.',25)
    d.save();source(name,'''
class DatabaseFunctions <<repository>> {
 - jdbc : JdbcTemplate
 - entities : EntityManager
 + reserve(claim, finder) : long
 + bid(auction, bidder, amount, key) : long
 + closeDue(limit) : void
}
class SqlStore <<repository>> {
 + update(sql, args) : int
 + one(sql, args) : Map
}
interface UserRepository
interface ListingRepository
interface Repository
class User <<immutable entity>>
class Listing <<immutable entity>>
class JdbcTemplate
class EntityManager
interface DataSource
UserRepository --|> Repository
ListingRepository --|> Repository
UserRepository ..> User
ListingRepository ..> Listing
DatabaseFunctions ..> JdbcTemplate
DatabaseFunctions ..> EntityManager
SqlStore ..> JdbcTemplate
SqlStore ..> EntityManager
JdbcTemplate ..> DataSource
EntityManager ..> DataSource
User "1" -- "0..*" Listing : authorId
''')

def processes():
    for name,title,svc,methods,entities in [
        ('03_returns','Бизнес-логика поиска и возврата','ReturnService',
         ['+ claim(actor, listing, evidence): long','+ reserve(finder, claim): long','+ confirm(actor, transfer): boolean'],
         [('Claim',['- listingId: Long','- claimantId: Long','- state: String']),('Transfer',['- claimId: Long','- finderConfirmedAt: OffsetDateTime','- ownerConfirmedAt: OffsetDateTime'])]),
        ('04_auctions','Бизнес-логика аукциона и подписки','AuctionService',
         ['+ requestPermission(): long','+ bid(actor, lot, amount, key): long','+ closeDue(limit): void'],
         [('Auction',['- permissionId: Long','- state: String','- winnerBidId: Long']),('Bid',['- auctionId: Long','- amount: BigDecimal','- requestKey: UUID'])])]:
        d=graphics.Drawing(name,1500,1100,title)
        box(d,450,110,600,svc,methods,height=220)
        box(d,40,475,440,'AccessPolicy',['+ audit(user): void','+ actor(user, paid): void'],'service',height=190)
        box(d,530,475,440,'DatabaseFunctions',['+ reserve(), confirm()' if svc=='ReturnService' else '+ bid(), closeDue()','PL/pgSQL этапа 2'],'repository',height=190)
        box(d,1020,475,440,'SqlStore',['+ id(sql, args): Long','+ update(sql, args): int'],'repository',height=190)
        for endx in [260,750,1240]:d.line([(750,330),(750,400),(endx,400),(endx,475)],dashed=True,arrow=True)
        for i,(entity,fields) in enumerate(entities):box(d,120+i*760,800,500,entity,fields,'immutable entity',height=215)
        d.line([(620,900),(880,900)])
        d.text(750,872,'1 — '+('0..1' if svc=='ReturnService' else '0..*'),24)
        d.text(750,735,'Записи связаны идентификаторами; связанные объекты загружаются явно.',25)
        d.save()
        body='class '+svc+' <<service>> {\n'+'\n'.join(methods)+'\n}\nclass AccessPolicy\nclass DatabaseFunctions\nclass SqlStore\n'
        for target in ['AccessPolicy','DatabaseFunctions','SqlStore']:body+=svc+' ..> '+target+'\n'
        for entity,fields in entities:body+='class '+entity+' <<immutable entity>> {\n'+'\n'.join(fields)+'\n}\n'
        if svc=='ReturnService':body+='Claim "1" -- "0..1" Transfer : claimId\n'
        else:body+='Auction "1" -- "0..*" Bid : auctionId\n'
        source(name,body)

if __name__=='__main__':overview();persistence();processes();print('4 UML diagrams: PNG, SVG, PlantUML')
