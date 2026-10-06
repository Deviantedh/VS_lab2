package portal;

import org.springframework.boot.SpringApplication;

public class TestLab1Application {

    public static void main(String[] args) {
        SpringApplication.from(Lab1Application::main).with(TestcontainersConfiguration.class).run(args);
    }

}
