package ru.itmo.is.poteryashki.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import ru.itmo.is.poteryashki.service.*;
import java.util.*;
import java.io.IOException;

@Controller @RequiredArgsConstructor
public class ListingPages {
    private final WebQueries queries;
    private final ListingService listings;
    private final ReturnService returns;
    private final PrivateFiles files;
    public record ListingForm(String kind,long category,long location,String title,String description,String eventAt,String eventUntil,Long custodian,String officialAt){
        ListingService.Draft draft(){return new ListingService.Draft(kind,category,location,title,description,UiText.time(eventAt),time(eventUntil),custodian,time(officialAt));}
        private java.time.OffsetDateTime time(String value){return value==null||value.isBlank()?null:UiText.time(value);}
    }
    private void choices(Model m){m.addAttribute("categories",queries.categories());m.addAttribute("locations",queries.locations());m.addAttribute("organizations",queries.metro());}
    @GetMapping("/catalog") String catalog(@AuthenticationPrincipal WebIdentity me,@RequestParam(required=false) String text,@RequestParam(required=false) String kind,@RequestParam(required=false) Long category,@RequestParam(required=false) Long location,@RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(required=false) Long cursor,Model m){
        var p=queries.profile(me.userId());if(p.get("paid_until")==null||!p.get("state").equals("verified")||p.get("email_confirmed_at")==null)return "redirect:/subscriptions";
        choices(m);var rows=queries.catalog(me.userId(),text,kind==null||kind.isBlank()?null:kind,category,location,from==null||from.isBlank()?null:UiText.time(from),to==null||to.isBlank()?null:UiText.time(to),cursor);
        m.addAttribute("listings",rows);m.addAttribute("next",rows.size()==24?rows.get(rows.size()-1).get("id"):null);return "catalog";
    }
    @GetMapping("/listings/new") String create(Model m){choices(m);m.addAttribute("item",Collections.emptyMap());return "listing-form";}
    @PostMapping("/listings") String create(@AuthenticationPrincipal WebIdentity me,@ModelAttribute ListingForm form){long id=listings.create(me.userId(),form.draft());return "redirect:/listings/"+id+"/edit";}
    @GetMapping("/listings/{id}") String detail(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,Model m){m.addAttribute("item",queries.listing(me.userId(),id));m.addAttribute("photos",queries.photos(me.userId(),id));return "listing";}
    @GetMapping("/listings/{id}/edit") String edit(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,Model m){choices(m);m.addAttribute("item",queries.ownedListing(me.userId(),id));m.addAttribute("attributes",listings.privateAttributes(me.userId(),id));m.addAttribute("photos",queries.photos(me.userId(),id));return "listing-form";}
    @PostMapping("/listings/{id}/edit") String edit(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@ModelAttribute ListingForm form){listings.edit(me.userId(),id,form.draft());return "redirect:/listings/"+id+"/edit";}
    @PostMapping("/listings/{id}/submit") String submit(@AuthenticationPrincipal WebIdentity me,@PathVariable long id){listings.submit(me.userId(),id);return "redirect:/account";}
    @PostMapping("/listings/{id}/archive") String archive(@AuthenticationPrincipal WebIdentity me,@PathVariable long id){listings.archive(me.userId(),id);return "redirect:/account";}
    @PostMapping("/listings/{id}/attributes") String attributes(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam String name,@RequestParam String value){listings.putPrivateAttribute(me.userId(),id,name,value);return "redirect:/listings/"+id+"/edit";}
    @PostMapping("/listings/{id}/photos") String photo(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam MultipartFile file,@RequestParam int position)throws IOException{
        queries.ownedListing(me.userId(),id);var object=files.image(file,"listings/"+id);
        try{listings.attachImage(me.userId(),id,object.key(),object.type(),object.size(),position);}catch(RuntimeException e){files.delete(object.key());throw e;}return "redirect:/listings/"+id+"/edit";
    }
    @PostMapping("/listings/{id}/claims") String claim(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam String evidence){returns.claim(me.userId(),id,evidence);return "redirect:/claims";}
}
