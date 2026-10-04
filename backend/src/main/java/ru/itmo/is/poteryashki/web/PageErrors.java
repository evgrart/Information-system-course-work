package ru.itmo.is.poteryashki.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.web.servlet.error.ErrorController;
import jakarta.servlet.http.*;
import java.util.NoSuchElementException;

@ControllerAdvice
public class PageErrors {
    @ExceptionHandler(SecurityException.class) @ResponseStatus(HttpStatus.FORBIDDEN)
    String denied(SecurityException e,Model m){m.addAttribute("message","Действие недоступно. Проверьте вход, статус профиля и подписку. Для изменения записи нужны права её участника.");return "failure";}
    @ExceptionHandler(NoSuchElementException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    String missing(Model m){m.addAttribute("message","Запись не найдена или недоступна этому пользователю.");return "failure";}
    @ExceptionHandler({IllegalArgumentException.class,DataAccessException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.multipart.MaxUploadSizeExceededException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    String invalid(Exception e,Model m){m.addAttribute("message","Не удалось выполнить действие. Проверьте заполнение полей и текущее состояние записи. Пароль должен содержать от 12 символов и не более 72 байт; файл — не более 5 МБ. Решение могло уже измениться в другой вкладке.");return "failure";}
    @Controller static class Errors implements ErrorController {
        @RequestMapping("/error") String error(HttpServletRequest request,Model m){m.addAttribute("message","Страница недоступна. Вернитесь в личный кабинет и повторите действие со страницы приложения.");return "failure";}
    }
}
