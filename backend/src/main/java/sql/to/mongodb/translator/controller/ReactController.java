package sql.to.mongodb.translator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ReactController {
    @RequestMapping(value = {"/", "/{path:[^.]*}"})
    public String forward() {
        return "forward:/index.html";  // Все запросы перенаправляются на index.html
    }
}
