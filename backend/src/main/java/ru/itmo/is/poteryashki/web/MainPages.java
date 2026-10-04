package ru.itmo.is.poteryashki.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import ru.itmo.is.poteryashki.service.*;
import java.util.UUID;

@Controller @RequiredArgsConstructor
public class MainPages {
    private final WebQueries queries;
    private final AdministrationService admin;
    private final SubscriptionService subscriptions;
    private final PrivateMailbox mail;
    @Value("${app.demo-payments}") private boolean demoPayments;
    @GetMapping("/") String home(Model m){m.addAttribute("counts",queries.counts());return "home";}
    @GetMapping("/metro") String metro(Model m){m.addAttribute("organizations",queries.metro());return "metro";}
    @GetMapping("/account") String account(@AuthenticationPrincipal WebIdentity me,Model m){
        m.addAttribute("listings",queries.ownListings(me.userId()));m.addAttribute("demoMail",mail.demo());
        var p=queries.profile(me.userId());m.addAttribute("notifications",p.get("state").equals("verified")&&p.get("email_confirmed_at")!=null?admin.notifications(me.userId()):java.util.List.of());return "account";
    }
    @GetMapping("/subscriptions") String subscription(@AuthenticationPrincipal WebIdentity me,Model m){
        m.addAttribute("tariffs",queries.tariffs());m.addAttribute("orders",queries.orders(me.userId()));m.addAttribute("key",UUID.randomUUID());m.addAttribute("event",UUID.randomUUID());m.addAttribute("demoPayments",demoPayments);return "subscriptions";
    }
    @PostMapping("/subscriptions/orders") String order(@AuthenticationPrincipal WebIdentity me,@RequestParam long tariff,@RequestParam UUID key){subscriptions.createOrder(me.userId(),tariff,key);return "redirect:/subscriptions";}
    @PostMapping("/subscriptions/orders/{id}/pay") String pay(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam UUID event){AccessPolicy.require(demoPayments,"Demo payments disabled");subscriptions.payDemo(me.userId(),id,event);return "redirect:/account";}
    @PostMapping("/notifications/{id}/read") String read(@AuthenticationPrincipal WebIdentity me,@PathVariable long id){admin.markRead(me.userId(),id);return "redirect:/account";}
    @PostMapping("/complaints") String complaint(@AuthenticationPrincipal WebIdentity me,@RequestParam(required=false) Long listing,@RequestParam(required=false) Long user,@RequestParam String reason){admin.complain(me.userId(),listing,user,reason);return "redirect:/account";}
}
