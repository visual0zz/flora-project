package com.flora.cache.store;

import com.flora.cache.*;

import java.time.Duration;
import java.util.Map;

public class CacheListenerAdapter<K, V> implements ObservableCache<K,V>,ObservableMemoryCache<K, V>, ObservableBoundedCache<K, V>{
    private CacheListenerAdapter(){}
    public ObservableMemoryCache<K,V> of(MemoryCache<K, V> cache){
        return new CacheListenerAdapter<>();
    }
    public ObservableBoundedCache<K, V> of(BoundedCache<K, V> cache){
        return new CacheListenerAdapter<>();
    }
    public ObservableCache<K, V> of(Cache<K, V> cache){
        return new CacheListenerAdapter<>();
    }
    @Override
    public void addListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {

    }

    @Override
    public void removeListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {

    }

    @Override
    public void removeListeners(CacheEventType type) {

    }

    @Override
    public void addListeners(Map<CacheEventType, CacheEventListener<? super K, ? super V>> listeners) {

    }

    @Override
    public void removeAllListeners() {
    }

    @Override
    public void put(K key, V value) {

    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        return false;
    }

    @Override
    public void put(K key, V value, Duration duration) {

    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        return false;
    }

    @Override
    public V get(K key) {
        return null;
    }

    @Override
    public boolean containsKey(K key) {
        return false;
    }

    @Override
    public void setTtl(K key, Duration duration) {

    }

    @Override
    public Duration ttl(K key) {
        return null;
    }

    @Override
    public V remove(K key) {
        return null;
    }

    @Override
    public void clear() {

    }

    @Override
    public long approxCount() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {

    }

    @Override
    public EvictionPolicy<K, V> evictionPolicy() {
        return null;
    }

    @Override
    public long cleanUp() {
        return 0;
    }

    @Override
    public boolean isFull() {
        return false;
    }

    @Override
    public long capacity() {
        return 0;
    }
}
