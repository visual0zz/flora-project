/**
 * 输出接口与扇出实现。
 * <p>命令通过 {@link com.flora.shell.output.Output} 写输出（单一门面）；
 * 业务输出汇实现 {@link com.flora.shell.output.OutputSink} 接收扇出的文本。
 * 二者是"一个接口、两个视角"——调用方看 {@code Output}，输出汇实现 {@code OutputSink}。</p>
 */
package com.flora.shell.output;
