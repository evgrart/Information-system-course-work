package ru.itmo.is.poteryashki.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice @RequiredArgsConstructor
public class PageModels {
    private final WebQueries queries;
    @ModelAttribute void common(Authentication authentication,Model model,HttpServletRequest request){
        WebIdentity me=authentication!=null&&authentication.getPrincipal() instanceof WebIdentity w?w:null;
        model.addAttribute("me",me);model.addAttribute("path",request.getRequestURI());model.addAttribute("paid",false);
        if(me!=null&&!request.getRequestURI().equals("/error")){
            var profile=queries.profile(me.userId());model.addAttribute("profile",profile);
            model.addAttribute("paid",profile.get("paid_until")!=null&&profile.get("state").equals("verified")&&profile.get("email_confirmed_at")!=null);
        }
    }
}
