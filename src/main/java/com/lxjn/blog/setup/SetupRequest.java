package com.lxjn.blog.setup;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SetupRequest {
    /** 结构类仅用于组织首装接口的请求类型。 */
    private SetupRequest() {}

    public record Credentials(@NotBlank @Size(max = 50) String username,
                              @NotBlank @Size(max = 256) String password) {}

    public record Database(@NotBlank String type, @Size(max = 1024) String path,
                           String host, Integer port, String name, String username, String password,
                           @NotNull Boolean reuse) {}

    public record Redis(boolean enabled, String host, Integer port,
                        String username, String password, Integer database) {}

    public record DatabaseTest(@Valid @NotNull Credentials admin, @Valid @NotNull Database database) {}
    public record RedisTest(@Valid @NotNull Credentials admin, @Valid @NotNull Redis redis) {}
    public record Save(@Valid @NotNull Credentials admin, @Valid @NotNull Database database,
                       @Valid @NotNull Redis redis) {}
}
