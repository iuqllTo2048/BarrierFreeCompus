package cn.barrierfreecampus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenHashServiceTest {
    private final TokenHashService tokenHashService = new TokenHashService();

    @Test
    void shouldCreateDeterministicSha256Hash() {
        assertThat(tokenHashService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(tokenHashService.hash("abc")).hasSize(64);
    }
}
