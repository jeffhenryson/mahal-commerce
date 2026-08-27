package com.cernecommerce.adapter.out.email;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

@Component
class ThymeleafEmailRenderer {

    private final TemplateEngine templateEngine;

    ThymeleafEmailRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    String render(String templateName, Map<String, Object> variables) {
        Context ctx = new Context(Locale.forLanguageTag("pt-BR"));
        ctx.setVariables(variables);
        return templateEngine.process("email/" + templateName, ctx);
    }
}
