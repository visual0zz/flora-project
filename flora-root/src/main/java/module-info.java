/**
 * flora-root 模块定义文件。
 * <p>
 * 该模块导出核心 API 包，并声明对 {@code Converter} SPI 的使用。
 */
import com.flora.ai.api.spi.AiProvider;
import com.flora.java.Converter;

module com.flora.root {
    exports com.flora.algebra;
    exports com.flora.crypto.core;
    exports com.flora.crypto.core.bridge;
    exports com.flora.crypto.core.impl;
    exports com.flora.crypto.core.mode;
    exports com.flora.crypto.core.padding;
    exports com.flora.crypto.core.param;
    exports com.flora.crypto.core.keypair;
    exports com.flora.crypto.core.combinator;
    exports com.flora.crypto.core.interfaces;
    exports com.flora.crypto.core.interfaces.provider;
    exports com.flora.crypto.schemes;
    exports com.flora.crypto.schemes.keyexchange;
    exports com.flora.crypto.schemes.engine.kex;
    exports com.flora.entropy;
    exports com.flora.entropy.compress;
    exports com.flora.entropy.compress.engine;
    exports com.flora.tag;
    exports com.flora.cache;
    exports com.flora.fast.container.consumer;
    exports com.flora.fast.container.map;
    exports com.flora.fast.container.tuple;
    exports com.flora.container.tuple;
    exports com.flora.codec;
    exports com.flora.java;
    exports com.flora.os;
    exports com.flora.runtime.log;
    exports com.flora.runtime.config;
    exports com.flora.runtime.virtual.filesys;
    exports com.flora.runtime.virtual.filesys.backend;

    exports com.flora.codec.jsonl;
    exports com.flora.codec.jsonschema;

    exports com.flora.mock.jsonschema;
    exports com.flora.mock.regex;
    exports com.flora.mock.regex.automaton;

    exports com.flora.comm.ssh;
    exports com.flora.comm.ssh.annotations;
    exports com.flora.codec.asn1;
    exports com.flora.comm.ssh.jbcrypt;
    exports com.flora.comm.ssh.juz;
    exports com.flora.comm.ssh.compress;
    exports com.flora.entropy.compress.zlib;
    exports com.flora.comm.ssh.logging;

    exports com.flora.syntax.common.exceptions;
    exports com.flora.syntax.common.definition;
    exports com.flora.syntax.bracket;
    exports com.flora.syntax.expr;
    exports com.flora.syntax.peg;

    exports com.flora.ai;
    exports com.flora.ai.api;
    exports com.flora.ai.api.impl;
    exports com.flora.ai.api.spi;
    exports com.flora.ai.api.provider;
    exports com.flora.ai.api.provider.client;
    exports com.flora.ai.api.provider.protocol;
    exports com.flora.ai.orchestration;
    exports com.flora.entropy.mesure;
    exports com.flora.entropy.mesure.engine;
    exports com.flora.syntax.common;
    exports com.flora.runtime.config.impl;
    exports com.flora.concurrent.retry;

    uses Converter;
    uses AiProvider;

    requires java.net.http;
    requires static org.jetbrains.annotations;
}
