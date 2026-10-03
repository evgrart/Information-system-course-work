"""Generate immutable JPA projections from the deployed stage-two catalog."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'backend/src/main/java/ru/itmo/is/poteryashki/domain'
OUT.mkdir(parents=True,exist_ok=True)
def camel(s): return ''.join(x.title() for x in s.split('_'))
NAMES={'lf_users':'User','lf_user_roles':'UserRole','lf_categories':'Category','lf_roles':'Role',
       'lf_auction_permissions':'AuctionPermission','lf_audit_entries':'AuditEntry'}
def class_name(table): return NAMES.get(table,camel(table[3:-1] if table.endswith('s') else table[3:]))
def java_type(t):
    if t=='bigint': return 'Long'
    if t=='integer': return 'Integer'
    if t=='smallint': return 'Short'
    if t=='boolean': return 'Boolean'
    if t=='uuid': return 'UUID'
    if t=='date': return 'LocalDate'
    if t=='timestamp with time zone': return 'OffsetDateTime'
    if t.startswith('numeric'): return 'BigDecimal'
    return 'String'
for table in json.loads((ROOT/'docs/part2/model/catalog.json').read_text(encoding='utf-8')):
    cls=class_name(table['name'])
    pk=next(c['definition'] for c in table['constraints'] if c['type']=='p')
    keys=re.search(r'\((.*?)\)',pk).group(1).split(', ')
    lines=['package ru.itmo.is.poteryashki.domain;','',
           'import jakarta.persistence.*;','import lombok.*;',
           'import java.math.BigDecimal;','import java.time.*;','import java.util.UUID;',
           'import org.hibernate.annotations.Immutable;','import org.hibernate.annotations.JdbcTypeCode;',
           'import org.hibernate.type.SqlTypes;','',
           '@Entity @Immutable @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)',
           '@Table(name = "'+table['name']+'")']
    if len(keys)>1: lines.append('@IdClass('+cls+'Key.class)')
    lines.append('public class '+cls+' {')
    for col in table['columns']:
        name=col['name'];t=col['type'];field=name.split('_')[0]+''.join(x.title() for x in name.split('_')[1:])
        if t=='tsvector':
            lines.extend(['    @Transient','    private String '+field+';'])
            continue
        if name in keys: lines.append('    @Id')
        if t.startswith('character('): lines.append('    @JdbcTypeCode(SqlTypes.CHAR)')
        if t in ('jsonb','tsvector'):
            lines.append('    @JdbcTypeCode(SqlTypes.'+('JSON' if t=='jsonb' else 'OTHER')+')')
        lines.append('    @Column(name = "'+name+'", columnDefinition = "'+t+'")')
        lines.append('    private '+java_type(t)+' '+field+';')
    lines.append('}')
    (OUT/(cls+'.java')).write_text('\n'.join(lines)+'\n',encoding='utf-8')
    if len(keys)>1:
        fields='\n'.join('    private '+java_type(next(c['type'] for c in table['columns'] if c['name']==key))+' '+key.split('_')[0]+''.join(x.title() for x in key.split('_')[1:])+';' for key in keys)
        (OUT/(cls+'Key.java')).write_text('package ru.itmo.is.poteryashki.domain;\nimport lombok.*;\nimport java.io.Serializable;\n@Data @NoArgsConstructor\npublic class '+cls+'Key implements Serializable {\n'+fields+'\n}\n',encoding='utf-8')
print('Generated 27 immutable entity projections')
