package ru.itmo.is.poteryashki.service;

import java.time.OffsetDateTime;
import java.util.*;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.poteryashki.persistence.*;
import static ru.itmo.is.poteryashki.service.AccessPolicy.*;

@Service @RequiredArgsConstructor @Transactional
public class ListingService {
    private final SqlStore store;
    private final DatabaseFunctions functions;
    private final ListingRepository listings;
    private final AccessPolicy access;
    private final Validator validator;
    public record Draft(@Pattern(regexp="lost|found") @NotNull String kind,@Positive long categoryId,
            @Positive long locationId,@NotBlank @Size(max=120) String title,
            @NotBlank @Size(max=10000) String description,@NotNull @PastOrPresent OffsetDateTime eventAt,
            OffsetDateTime eventUntil,Long custodianOrgId,OffsetDateTime officialReportedAt) { }
    public long create(long actor,Draft draft) {
        store.one("select id from lf_users where id=? for update",actor);
        access.actor(actor,true);validate(draft);
        require(!store.exists("select count(*)>=10 from lf_listings where author_id=? and created_at>=date_trunc('day',clock_timestamp())",actor),"Daily listing limit exceeded");
        return store.id("insert into lf_listings(author_id,kind,category_id,location_id,title,description,event_at,event_until,custodian_org_id,official_reported_at) values (?,?,?,?,?,?,?,?,?,?) returning id",
                actor,draft.kind(),draft.categoryId(),draft.locationId(),draft.title(),draft.description(),draft.eventAt(),draft.eventUntil(),draft.custodianOrgId(),draft.officialReportedAt());
    }
    public void edit(long actor,long listing,Draft draft) {
        access.actor(actor,true);validate(draft);
        var row=owned(actor,listing);
        require(row.get("kind").equals(draft.kind()),"Listing kind is immutable");
        store.update("update lf_listings set category_id=?,location_id=?,title=?,description=?,event_at=?,event_until=?,custodian_org_id=?,official_reported_at=? where id=?",
                draft.categoryId(),draft.locationId(),draft.title(),draft.description(),draft.eventAt(),draft.eventUntil(),draft.custodianOrgId(),draft.officialReportedAt(),listing);
    }
    public void submit(long actor,long listing) {
        access.actor(actor,true);var row=owned(actor,listing);
        require(Set.of("draft","rejected").contains(row.get("state")),"Listing cannot be submitted");
        store.update("update lf_listings set state='pending',moderator_id=null,moderated_at=null where id=?",listing);
    }
    public void moderate(long moderator,long listing,boolean approve) {
        access.moderator(moderator);
        var row=store.one("select author_id,state from lf_listings where id=? for update",listing);
        require(owner(row,"author_id")!=moderator && row.get("state").equals("pending"),"Independent pending review required");
        store.update("update lf_listings set state=?,moderator_id=?,moderated_at=clock_timestamp() where id=?",approve?"published":"rejected",moderator,listing);
        store.update("insert into lf_outbox_events(kind,aggregate_id,payload) values ('listing.reviewed',?,jsonb_build_object('approved',?::boolean))",listing,approve);
    }
    public void archive(long actor,long listing) {
        access.actor(actor,false);var row=owned(actor,listing);
        require(Set.of("draft","pending","published","rejected").contains(row.get("state")),"Active return or closed history cannot be archived");
        store.update("update lf_listings set state='archived' where id=?",listing);
    }
    public List<Map<String,Object>> search(long actor,String text,String kind,Long category,Long location,
            OffsetDateTime from,OffsetDateTime to,Long cursor,int limit) {
        access.actor(actor,true);return functions.search(text,kind,category,location,from,to,cursor,limit);
    }
    public Map<String,Object> detail(long actor,long listing) {
        access.actor(actor,true);
        return store.one("select id,title,description,kind,event_at,event_until,state,category,city,place,metro_station,custodian,phone,instructions,source_url,verified_on from lf_public_listings where id=?",listing);
    }
    public List<Map<String,Object>> searchByState(long actor,String text,String kind,Long category,Long location,OffsetDateTime from,OffsetDateTime to,Long cursor,int limit,String state) {
        access.actor(actor,true);return functions.searchByState(text,kind,category,location,from,to,cursor,limit,state);
    }
    public List<Map<String,Object>> privateAttributes(long actor,long listing) {
        access.actor(actor,false);var row=owned(actor,listing);
        return store.rows("select name,value from lf_private_attributes where listing_id=?",owner(row,"id"));
    }
    public void putPrivateAttribute(long actor,long listing,String name,String value) {
        access.actor(actor,true);owned(actor,listing);
        require(name!=null && !name.isBlank() && value!=null && !value.isBlank(),"Attribute required");
        store.update("insert into lf_private_attributes(listing_id,name,value) values (?,?,?) on conflict(listing_id,name) do update set value=excluded.value",listing,name,value);
    }
    /** Metadata of an already verified private storage object; binary upload belongs to its adapter. */
    public long attachImage(long actor,long listing,String objectKey,String mediaType,long size,int position) {
        access.actor(actor,true);var row=owned(actor,listing);
        require(Set.of("draft","pending","rejected").contains(row.get("state")),"Edit images before publication");
        require(objectKey!=null && objectKey.matches("listings/"+listing+"/[a-zA-Z0-9._-]+"),"Object key must belong to listing");
        require(Set.of("image/jpeg","image/png","image/webp").contains(mediaType) && size>0 && size<=5*1024*1024 && position>=1 && position<=5,"Invalid image metadata");
        return store.id("insert into lf_listing_images(listing_id,object_key,media_type,size_bytes,position) values (?,?,?,?,?) returning id",listing,objectKey,mediaType,size,position);
    }
    public List<Map<String,Object>> organizations(long actor) {
        access.actor(actor,true);
        return store.rows("select id,name,phone,address,instructions,source_url,verified_on,verified_on<current_date-30 as needs_review from lf_organizations order by id");
    }
    public String state(long actor,long listing) {
        access.actor(actor,false);
        var entity=listings.findById(listing).orElseThrow();
        require(entity.getAuthorId()==actor,"Not your listing");
        return entity.getState();
    }
    private Map<String,Object> owned(long actor,long listing) {
        var row=store.one("select id,author_id,kind,state from lf_listings where id=? for update",listing);
        require(owner(row,"author_id")==actor,"Not your listing");return row;
    }
    private void validate(Draft draft) {
        if (draft==null || !validator.validate(draft).isEmpty()) throw new IllegalArgumentException("Invalid listing fields");
    }
}
