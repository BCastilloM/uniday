package cl.grupo4.uniday.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    public WebConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] protectedPatterns = {"/semestres", "/semestres/**", "/materias", "/materias/**"};

        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(protectedPatterns);
    }
}
