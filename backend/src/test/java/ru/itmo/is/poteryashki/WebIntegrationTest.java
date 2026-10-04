package ru.itmo.is.poteryashki;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import ru.itmo.is.poteryashki.web.*;
import ru.itmo.is.poteryashki.persistence.SqlStore;
import ru.itmo.is.poteryashki.service.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties="app.jobs-enabled=false") @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class WebIntegrationTest {
    @Autowired MockMvc mvc;@Autowired WebSessions sessions;@Autowired WebTokens tokens;@Autowired SqlStore store;
    @Autowired ListingService listings;@Autowired AccountService accounts;@Autowired ReturnService returns;
    @Autowired SubscriptionService subscriptions;
    private static final String PASS="DemoCourse2026!";
    @BeforeAll static void guard(){assertEquals("lf3check_",System.getenv("DB_PREFIX"));}
    Cookie login(String email){return new Cookie("LF_ACCESS",tokens.access(sessions.login(email,PASS).identity()));}
    Cookie finder(){return login("finder@example.invalid");}
    @ParameterizedTest @ValueSource(strings={"/","/metro","/auth/login","/auth/register","/auth/forgot","/auth/reset","/auth/confirm"})
    void publicPagesRender(String path)throws Exception{mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/html"));}
    @ParameterizedTest @ValueSource(strings={"/account","/account/mail","/subscriptions","/catalog","/listings/new","/listings/1","/listings/1/edit","/claims","/conversations/1","/auctions","/auctions/1"})
    void subscriberPagesRenderWithoutSecrets(String path)throws Exception{mvc.perform(get(path).cookie(finder())).andExpect(status().isOk()).andExpect(content().string(not(containsString("password_hash"))));}
    @ParameterizedTest @ValueSource(strings={"profiles","listings","permissions","complaints","transfers","audit"})
    void moderatorQueuesRender(String tab)throws Exception{mvc.perform(get("/staff").param("tab",tab).cookie(login("moderator@example.invalid"))).andExpect(status().isOk());}
    @Test void administratorPageRenders()throws Exception{mvc.perform(get("/admin").cookie(login("admin@example.invalid"))).andExpect(status().isOk());}
    @Test void anonymousCannotReadCatalog()throws Exception{mvc.perform(get("/catalog")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/auth/login"));}
    @Test void writeWithoutCsrfRejected()throws Exception{mvc.perform(post("/listings/1/archive").cookie(finder())).andExpect(status().isForbidden());}
    @Test void subscriberCannotBecomeModeratorByUrl()throws Exception{mvc.perform(get("/staff").cookie(finder())).andExpect(status().isForbidden());mvc.perform(get("/admin").cookie(finder())).andExpect(status().isForbidden());}
    @Test void pendingAccountSeesGateInsteadOfCatalog()throws Exception{mvc.perform(get("/catalog").cookie(login("pending@example.invalid"))).andExpect(redirectedUrl("/subscriptions"));}
    @Test void anotherUserCannotReadPrivateFields()throws Exception{mvc.perform(get("/listings/1/edit").cookie(login("owner@example.invalid"))).andExpect(status().isForbidden());mvc.perform(get("/listings/1").cookie(login("owner@example.invalid"))).andExpect(content().string(not(containsString("Тестовая надпись"))));}
    @Test void outsiderCannotReadConversation()throws Exception{mvc.perform(get("/conversations/1").cookie(login("buyer@example.invalid"))).andExpect(status().isForbidden());}
    @Test void publicListingEscapesMarkup()throws Exception{
        var draft=new ListingService.Draft("found",1,1,"<script>alert(1)</script>","<img src=x onerror=alert(1)>",java.time.OffsetDateTime.now().minusHours(1),null,null,null);
        long id=listings.create(3,draft);listings.submit(3,id);listings.moderate(1,id,true);
        mvc.perform(get("/listings/"+id).cookie(finder())).andExpect(content().string(containsString("&lt;script&gt;"))).andExpect(content().string(not(containsString("<script>alert"))));
    }
    @Test void loginSetsProtectedCookies()throws Exception{var result=mvc.perform(post("/auth/login").with(csrf()).param("email","finder@example.invalid").param("password",PASS)).andExpect(redirectedUrl("/account")).andReturn();String headers=String.join(";",result.getResponse().getHeaders("Set-Cookie"));assertTrue(headers.contains("HttpOnly"));assertTrue(headers.contains("SameSite=Lax"));assertFalse(headers.contains("password"));}
    @Test void logoutRevokesExistingAccess()throws Exception{var issued=sessions.login("finder@example.invalid",PASS);var access=new Cookie("LF_ACCESS",tokens.access(issued.identity()));mvc.perform(post("/auth/logout").with(csrf()).cookie(access,new Cookie("LF_REFRESH",issued.refreshToken()))).andExpect(redirectedUrl("/"));mvc.perform(get("/account").cookie(access)).andExpect(redirectedUrl("/auth/login"));}
    @Test void refreshIsRotatedAndCannotReplay(){var issued=sessions.login("finder@example.invalid",PASS);var next=sessions.refresh(issued.refreshToken());assertNotEquals(issued.refreshToken(),next.refreshToken());assertThrows(NoSuchElementException.class,()->sessions.refresh(issued.refreshToken()));}
    @Test void passwordResetImmediatelyInvalidatesAccess()throws Exception{Cookie cookie=finder();String raw=accounts.requestPasswordReset("finder@example.invalid").orElseThrow();accounts.resetPassword(raw,"NewCoursePassword2026!");mvc.perform(get("/account").cookie(cookie)).andExpect(redirectedUrl("/auth/login"));}
    @Test void currentRolesAreCheckedForEachRequest()throws Exception{var c=login("moderator@example.invalid");store.update("delete from lf_user_roles where user_id=1 and role_code='moderator'");mvc.perform(get("/staff").cookie(c)).andExpect(status().isForbidden());}
    @Test void forgottenPasswordDoesNotDiscloseToken()throws Exception{var r=mvc.perform(post("/auth/forgot").with(csrf()).param("email","owner@example.invalid")).andExpect(redirectedUrl("/auth/forgot?sent")).andReturn();assertFalse(r.getResponse().getContentAsString().contains("token="));mvc.perform(get("/account/mail")).andExpect(redirectedUrl("/auth/login"));}
    @Test void bidPostedThroughMvcUsesDatabaseFunction()throws Exception{var key=UUID.randomUUID();mvc.perform(post("/auctions/1/bids").with(csrf()).cookie(login("owner@example.invalid")).param("amount","1200").param("key",key.toString())).andExpect(redirectedUrl("/auctions/1"));assertEquals(1L,store.id("select count(*) from lf_bids where request_key=?",key));}
    @Test void expiredSubscriptionCanConfirmTransferThroughPage()throws Exception{long transfer=returns.reserve(3,1);var c=login("owner@example.invalid");store.update("update lf_subscriptions set starts_at=starts_at-interval '60 days',ends_at=ends_at-interval '60 days' where user_id=4");mvc.perform(post("/transfers/"+transfer+"/confirm").with(csrf()).cookie(c)).andExpect(redirectedUrl("/claims"));}
    @Test void malformedJwtCannotAuthenticate()throws Exception{mvc.perform(get("/account").cookie(new Cookie("LF_ACCESS","broken"))).andExpect(redirectedUrl("/auth/login"));}
    @Test void sampleClaimsHaveWorkingConversationLinks(){assertFalse(store.exists("select exists(select 1 from lf_claims c where not exists(select 1 from lf_conversations d where d.claim_id=c.id))"));}
    @Test void pendingOrderOffersDemoPayment()throws Exception{subscriptions.createOrder(3,1,UUID.randomUUID());mvc.perform(get("/subscriptions").cookie(finder())).andExpect(content().string(containsString("Оплатить в учебном режиме")));}
    // csrf() used by other scenarios substitutes a test repository; this check needs the real cookie repository.
    @org.springframework.test.annotation.DirtiesContext(methodMode=org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    @Test void createFormContainsCsrfAndSubmitsActualFields()throws Exception{
        Cookie access=finder();var page=mvc.perform(get("/listings/new").cookie(access)).andReturn();
        var matcher=java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString().split("<main",2)[1]);assertTrue(matcher.find(),"Creation form must carry CSRF");
        String cookieHeader=page.getResponse().getHeaders("Set-Cookie").stream().filter(h->h.startsWith("LF_CSRF=")).findFirst().orElseThrow();
        Cookie csrfCookie=new Cookie("LF_CSRF",cookieHeader.substring("LF_CSRF=".length()).split(";",2)[0]);
        var submitted=mvc.perform(post("/listings").cookie(access,csrfCookie).param("_csrf",matcher.group(1)).param("kind","found").param("category","1").param("location","1").param("title","Новая находка").param("description","Учебное описание").param("eventAt",java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Moscow")).minusHours(1).toString()).param("eventUntil","").param("custodian","").param("officialAt","")).andReturn();
        assertEquals(302,submitted.getResponse().getStatus(),String.valueOf(submitted.getResolvedException()));
    }
}
