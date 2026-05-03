package com.example.guardian.service;

import com.example.guardian.model.HostConfig;
import com.example.guardian.model.HostConnectionMode;
import com.example.guardian.repository.HostConfigRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Создает минимальные стартовые данные для пустой базы.
 */
@Component
public class DataBootstrap implements ApplicationRunner {

    private final HostConfigRepository hostConfigRepository;

    public DataBootstrap(HostConfigRepository hostConfigRepository) {
        this.hostConfigRepository = hostConfigRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (hostConfigRepository.count() > 0) {
            return;
        }

        HostConfig localhost = new HostConfig();
        localhost.setName("Localhost");
        localhost.setConnectionMode(HostConnectionMode.LOCAL);
        localhost.setAddress("127.0.0.1");
        localhost.setDescription("Default local host created automatically");
        hostConfigRepository.save(localhost);
    }
}
