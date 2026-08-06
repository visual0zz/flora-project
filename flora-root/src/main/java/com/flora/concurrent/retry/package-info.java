/**
 * 并发容错工具:重试机制。
 * <p>提供重试执行器({@link com.flora.concurrent.retry.Retryer})、重试策略
 * ({@link com.flora.concurrent.retry.RetryPolicy})与退避策略
 * ({@link com.flora.concurrent.retry.Backoff}),用于对易失败操作执行有限次重试。</p>
 */
package com.flora.concurrent.retry;
