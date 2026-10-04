package ru.itmo.is.poteryashki;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WEB_BASE_URL",matches="http://127\\.0\\.0\\.1:[0-9]+")
class BrowserFlowTest {
    String base=System.getenv("WEB_BASE_URL");Path root=Path.of(System.getenv().getOrDefault("COURSE_ROOT",".."));
    void go(Page p,String url){assertTrue(p.navigate(base+url).status()<400,"Page failed: "+url);}
    void login(Page p,String email){go(p,"/auth/login");p.locator("input[name=email]").fill(email);p.locator("input[name=password]").fill("DemoCourse2026!");p.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Войти").setExact(true)).click();p.waitForURL("**/account");}
    void shot(Page p,String name)throws Exception{Files.createDirectories(root.resolve("docs/part4/screenshots"));p.screenshot(new Page.ScreenshotOptions().setPath(root.resolve("docs/part4/screenshots/"+name+".png")).setFullPage(true));}
    @Test void completeBusinessProcessesInRealBrowser()throws Exception{
        assertEquals("lf3check_",System.getenv("DB_PREFIX"));
        try(var pw=Playwright.create();var browser=pw.chromium().launch(new BrowserType.LaunchOptions().setChannel("msedge").setHeadless(true))){
            var failures=new ArrayList<String>();
            var finder=browser.newContext(new Browser.NewContextOptions().setViewportSize(1440,1000));var owner=browser.newContext(new Browser.NewContextOptions().setViewportSize(1440,1000));var staff=browser.newContext(new Browser.NewContextOptions().setViewportSize(1440,1000));
            Page f=finder.newPage(),o=owner.newPage(),s=staff.newPage();for(Page p:List.of(f,o,s)){p.onPageError(failures::add);p.onResponse(r->{if(r.status()>=500)failures.add(r.status()+" "+r.url());});}
            go(f,"/");shot(f,"01-home");login(f,"finder@example.invalid");go(f,"/catalog");assertTrue(f.getByRole(AriaRole.HEADING,new Page.GetByRoleOptions().setName("Каталог вещей")).isVisible());shot(f,"02-catalog");
            go(f,"/listings/new");f.locator("input[name=title]").fill("Учебная находка для проверки возврата");f.locator("textarea[name=description]").fill("Найден тестовый брелок рядом с парком. Особая примета известна владельцу.");f.locator("select[name=category]").selectOption("1");f.locator("select[name=location]").selectOption("1");f.locator("input[name=eventAt]").fill(LocalDateTime.now(ZoneId.of("Europe/Moscow")).minusHours(1).withSecond(0).withNano(0).toString());f.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Сохранить черновик")).click();f.waitForURL("**/edit");
            Path image=root.resolve(".tools/upload-check.png");Files.createDirectories(image.getParent());ImageIO.write(new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB),"PNG",image.toFile());f.locator("input[name=file]").setInputFiles(image);f.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Загрузить фото")).click();f.waitForURL("**/edit");assertTrue(f.locator(".photo-strip img").count()>0);shot(f,"03-listing-editor");f.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Отправить на модерацию")).click();f.waitForURL("**/account");
            login(s,"moderator@example.invalid");go(s,"/staff?tab=listings");var review=s.locator("article.panel").filter(new Locator.FilterOptions().setHasText("Учебная находка для проверки возврата"));assertEquals(1,review.count());shot(s,"04-moderation");review.getByRole(AriaRole.BUTTON,new Locator.GetByRoleOptions().setName("Опубликовать")).click();
            login(o,"owner@example.invalid");go(o,"/catalog?text=Учебная");o.getByRole(AriaRole.LINK,new Page.GetByRoleOptions().setName("Учебная находка для проверки возврата").setExact(true)).click();o.locator("textarea[name=evidence]").fill("На кольце есть синяя метка — это мой учебный брелок.");o.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Подать заявку на возврат")).click();o.waitForURL("**/claims");
            go(f,"/claims");var request=f.locator("article.panel").filter(new Locator.FilterOptions().setHasText("Учебная находка для проверки возврата"));request.getByRole(AriaRole.BUTTON,new Locator.GetByRoleOptions().setName("Согласовать возврат")).click();go(f,"/claims");f.locator("article.panel").filter(new Locator.FilterOptions().setHasText("Учебная находка для проверки возврата")).getByRole(AriaRole.LINK,new Locator.GetByRoleOptions().setName("Открыть диалог")).click();f.locator("textarea[name=body]").fill("Давайте встретимся у входа в парк в 18:00.");f.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Отправить").setExact(true)).click();shot(f,"05-conversation");
            go(o,"/claims");o.locator("article.panel").filter(new Locator.FilterOptions().setHasText("Учебная находка для проверки возврата")).getByRole(AriaRole.BUTTON,new Locator.GetByRoleOptions().setName("Подтвердить передачу вещи")).click();go(f,"/claims");f.locator("article.panel").filter(new Locator.FilterOptions().setHasText("Учебная находка для проверки возврата")).getByRole(AriaRole.BUTTON,new Locator.GetByRoleOptions().setName("Подтвердить передачу вещи")).click();assertTrue(f.locator("article.panel").filter(new Locator.FilterOptions().setHasText("Учебная находка для проверки возврата")).innerText().contains("Передача завершена"));shot(f,"06-return");
            go(o,"/auctions/1");o.locator("input[name=amount]").fill("1200");o.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Сделать ставку")).click();assertTrue(o.locator("main").innerText().contains("1200"));shot(o,"07-auction");
            go(f,"/metro");shot(f,"08-metro");go(s,"/staff?tab=permissions");assertTrue(s.locator("main").innerText().contains("Допуски к продаже"));
            var admin=browser.newPage(new Browser.NewPageOptions().setViewportSize(1440,1000));login(admin,"admin@example.invalid");go(admin,"/admin");shot(admin,"09-admin");
            var newcomer=browser.newPage();go(newcomer,"/auth/register");newcomer.locator("input[name=email]").fill("browser-user@example.invalid");newcomer.locator("input[name=name]").fill("Учебный пользователь");newcomer.locator("input[name=password]").fill("DemoCourse2026!");newcomer.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Зарегистрироваться")).click();newcomer.waitForURL("**/account");go(newcomer,"/account/mail");newcomer.getByRole(AriaRole.LINK,new Page.GetByRoleOptions().setName("Открыть письмо →")).click();newcomer.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Подтвердить").setExact(true)).click();go(s,"/staff");var profile=s.locator("article.panel").filter(new Locator.FilterOptions().setHasText("Учебный пользователь"));profile.locator("textarea[name=reason]").fill("Профиль проверен на учебных данных");profile.getByRole(AriaRole.BUTTON,new Locator.GetByRoleOptions().setName("Одобрить профиль")).click();
            login(newcomer,"browser-user@example.invalid");go(newcomer,"/subscriptions");newcomer.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Создать заказ")).first().click();newcomer.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Оплатить в учебном режиме")).first().click();newcomer.waitForURL("**/account");assertTrue(newcomer.locator("main").innerText().contains("Действует"));shot(newcomer,"10-subscription");
            f.setViewportSize(390,844);go(f,"/catalog");assertEquals(true,f.evaluate("document.documentElement.scrollWidth <= window.innerWidth"));shot(f,"11-mobile-catalog");assertTrue(failures.isEmpty(),failures.toString());
        }
    }
}
