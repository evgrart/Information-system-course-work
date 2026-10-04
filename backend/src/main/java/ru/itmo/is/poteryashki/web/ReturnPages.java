package ru.itmo.is.poteryashki.web;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import ru.itmo.is.poteryashki.service.ReturnService;

@Controller @RequiredArgsConstructor
public class ReturnPages {
    private final WebQueries queries;private final ReturnService returns;
    @GetMapping("/claims") String claims(@AuthenticationPrincipal WebIdentity me,Model m){m.addAttribute("claims",queries.claims(me.userId()));return "claims";}
    @GetMapping("/conversations/{id}") String conversation(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,Model m){m.addAttribute("conversation",queries.conversation(me.userId(),id));m.addAttribute("messages",returns.messages(me.userId(),id));return "conversation";}
    @PostMapping("/conversations/{id}/messages") String message(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@RequestParam String body){returns.send(me.userId(),id,body);return "redirect:/conversations/"+id;}
    @PostMapping("/claims/{id}/{action:reserve|reject|cancel}") String change(@AuthenticationPrincipal WebIdentity me,@PathVariable long id,@PathVariable String action){switch(action){case "reserve"->returns.reserve(me.userId(),id);case "reject"->returns.reject(me.userId(),id);case "cancel"->returns.cancel(me.userId(),id);}return "redirect:/claims";}
    @PostMapping("/transfers/{id}/confirm") String confirm(@AuthenticationPrincipal WebIdentity me,@PathVariable long id){returns.confirm(me.userId(),id);return "redirect:/claims";}
}
