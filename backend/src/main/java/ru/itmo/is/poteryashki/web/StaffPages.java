package ru.itmo.is.poteryashki.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import ru.itmo.is.poteryashki.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Controller @RequiredArgsConstructor
public class StaffPages {
    private final WebQueries queries;private final AccountService accounts;private final ListingService listings;
    private final AuctionService auctions;private final AdministrationService admin;private final ReturnService returns;
    @GetMapping("/staff") String staff(@AuthenticationPrincipal WebIdentity me,@RequestParam(defaultValue="profiles") String tab,@RequestParam(required=false) Long before,Model m){
        m.addAttribute("tab",tab);m.addAttribute("rows",switch(tab){
            case "profiles"->queries.verifications(me.userId());case "listings"->queries.pendingListings(me.userId());
            case "permissions"->queries.pendingPermissions(me.userId());case "complaints"->queries.complaints(me.userId());
            case "transfers"->queries.pendingTransfers(me.userId());case "audit"->admin.audit(me.userId(),before,50);
            default->throw new IllegalArgumentException("Unknown tab");});return "staff";
    }
    @PostMapping("/staff/profiles/{id}") String profile(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam boolean approve,@RequestParam String reason){accounts.reviewProfile(me.userId(),id,approve,reason);return "redirect:/staff";}
    @PostMapping("/staff/listings/{id}") String listing(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam boolean approve){listings.moderate(me.userId(),id,approve);return "redirect:/staff?tab=listings";}
    @PostMapping("/staff/permissions/{id}") String permission(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam boolean approve,@RequestParam String reason){auctions.reviewPermission(me.userId(),id,approve,reason);return "redirect:/staff?tab=permissions";}
    @PostMapping("/staff/complaints/{id}") String complaint(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam boolean resolved,@RequestParam String reason){admin.reviewComplaint(me.userId(),id,resolved,reason);return "redirect:/staff?tab=complaints";}
    @PostMapping("/staff/transfers/{id}") String transfer(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam String reason){returns.resolve(me.userId(),id,reason);return "redirect:/staff?tab=transfers";}
    @PostMapping("/staff/auctions/{id}/{action:close|cancel}") String auction(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@PathVariable String action){if(action.equals("close"))auctions.finalizeAuction(me.userId(),id);else auctions.cancel(me.userId(),id);return "redirect:/staff";}
    @PostMapping("/staff/users/{id}/block") String block(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam String reason){admin.block(me.userId(),id,reason);return "redirect:/staff?tab=complaints";}
    @PostMapping("/staff/users/block") String blockForm(@AuthenticationPrincipal WebIdentity me,@RequestParam long id,@RequestParam String reason){return block(me,id,reason);}
    @PostMapping("/staff/auctions/cancel") String cancelForm(@AuthenticationPrincipal WebIdentity me,@RequestParam long id){auctions.cancel(me.userId(),id);return "redirect:/staff?tab=complaints";}
    @GetMapping("/staff/listings/{id}/inspect") String inspect(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,Model m){m.addAttribute("photos",queries.photos(me.userId(),id));return "inspection";}
    @GetMapping("/staff/complaints/{id}/conversations") String caseFiles(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,Model m){m.addAttribute("conversations",queries.caseConversations(me.userId(),id));return "case-conversations";}
    @GetMapping("/admin") String administration(@AuthenticationPrincipal WebIdentity me,Model m){m.addAttribute("users",queries.users(me.userId()));m.addAttribute("tariffs",queries.allTariffs(me.userId()));m.addAttribute("organizations",queries.metro());return "admin";}
    @PostMapping("/admin/users/{id}/roles") String role(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam String role,@RequestParam String reason,@RequestParam boolean grant){if(grant)admin.grantRole(me.userId(),id,role,reason);else admin.revokeRole(me.userId(),id,role,reason);return "redirect:/admin";}
    @PostMapping("/admin/tariffs") String tariff(@AuthenticationPrincipal WebIdentity me,@RequestParam String title,@RequestParam BigDecimal amount,@RequestParam int days){admin.tariff(me.userId(),title,amount,days);return "redirect:/admin";}
    @PostMapping("/admin/tariffs/{id}/retire") String retire(@AuthenticationPrincipal WebIdentity me,@PathVariable long id){admin.retireTariff(me.userId(),id);return "redirect:/admin";}
    @PostMapping("/admin/organizations/{id}") String organization(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam String phone,@RequestParam String address,@RequestParam String instructions,@RequestParam String source,@RequestParam LocalDate checked){admin.updateOrganization(me.userId(),id,phone,address,instructions,source,checked);return "redirect:/admin";}
}
