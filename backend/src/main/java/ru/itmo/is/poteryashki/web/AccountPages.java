package ru.itmo.is.poteryashki.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import jakarta.servlet.http.*;
import ru.itmo.is.poteryashki.service.AccountService;

@Controller @RequiredArgsConstructor
public class AccountPages {
    private final AccountService accounts;
    private final WebSessions sessions;
    private final WebTokens tokens;
    private final WebQueries queries;
    private final PrivateMailbox mail;
    @GetMapping("/auth/{page:login|register|forgot|reset|confirm}")
    String form(@PathVariable String page,@RequestParam(defaultValue="") String token,HttpServletRequest request,Model model){
        model.addAttribute("page",page);model.addAttribute("token",token);
        model.addAttribute("refreshAvailable",CookieAuthentication.cookie(request,"LF_REFRESH")!=null);return "auth";
    }
    @PostMapping("/auth/login") String login(@RequestParam String email,@RequestParam String password,HttpServletResponse response){
        tokens.issue(response,sessions.login(email,password));return "redirect:/account";
    }
    @PostMapping("/auth/register") String register(@RequestParam String email,@RequestParam String name,@RequestParam String password,HttpServletResponse response){
        if(email.length()>254||name.length()>80)throw new IllegalArgumentException("Account field too long");
        var registration=accounts.register(email,name,password);tokens.issue(response,sessions.login(email,password));
        mail.send(registration.userId(),email,"Подтверждение почты","Подтвердите адрес электронной почты. Ссылка действует один час.","/auth/confirm?token="+registration.confirmationToken());
        return "redirect:/account";
    }
    @PostMapping("/auth/confirm") String confirm(@RequestParam String token){accounts.confirmEmail(token);return "redirect:/account";}
    @PostMapping("/auth/forgot") String forgot(@RequestParam String email){
        accounts.requestPasswordReset(email).ifPresent(raw->mail.send(queries.accountForMail(email),email,"Восстановление пароля","Установите новый пароль. Ссылка одноразовая.","/auth/reset?token="+raw));
        return "redirect:/auth/forgot?sent";
    }
    @PostMapping("/auth/reset") String reset(@RequestParam String token,@RequestParam String password,HttpServletResponse response){accounts.resetPassword(token,password);tokens.clear(response);return "redirect:/auth/login?reset";}
    @PostMapping("/auth/refresh") String refresh(HttpServletRequest request,HttpServletResponse response){
        var raw=CookieAuthentication.cookie(request,"LF_REFRESH");if(raw==null)throw new SecurityException("Session expired");
        tokens.issue(response,sessions.refresh(raw));return "redirect:/account";
    }
    @PostMapping("/auth/logout") String logout(HttpServletRequest request,HttpServletResponse response){
        var raw=CookieAuthentication.cookie(request,"LF_REFRESH");if(raw!=null)sessions.logoutToken(raw);tokens.clear(response);return "redirect:/";
    }
    @PostMapping("/account/resubmit") String resubmit(@AuthenticationPrincipal WebIdentity me){accounts.submitProfile(me.userId());return "redirect:/account";}
    @PostMapping("/account/confirmation") String confirmation(@AuthenticationPrincipal WebIdentity me){String raw=accounts.resendConfirmation(me.userId());String email=queries.profile(me.userId()).get("email").toString();mail.send(me.userId(),email,"Подтверждение почты","Подтвердите адрес. Ссылка действует один час.","/auth/confirm?token="+raw);return "redirect:/account/mail";}
    @GetMapping("/account/mail") String inbox(@AuthenticationPrincipal WebIdentity me,Model model){model.addAttribute("letters",mail.letters(me.userId()));model.addAttribute("demoMail",mail.demo());return "mail";}
}
