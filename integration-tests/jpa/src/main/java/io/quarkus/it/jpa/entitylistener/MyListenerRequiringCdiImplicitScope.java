package io.quarkus.it.jpa.entitylistener;

import java.lang.annotation.Annotation;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;

import io.quarkus.arc.ClientProxy;

// No CDI scope here: it's implicit, which is allowed by the JPA spec
public class MyListenerRequiringCdiImplicitScope {
    private static final AtomicInteger instanceOrdinalSource = new AtomicInteger(0);

    @Inject
    MyCdiContext cdiContext;

    private final String ref;

    public MyListenerRequiringCdiImplicitScope() {
        int ordinal;
        if (!ClientProxy.class.isAssignableFrom(getClass())) { // Disregard CDI proxies extending this class
            ordinal = instanceOrdinalSource.getAndIncrement();
        } else {
            ordinal = -1;
        }
        this.ref = ReceivedEvent.objectRef(MyListenerRequiringCdiImplicitScope.class, ordinal);
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        receiveEvent(PreUpdate.class, entity);
    }

    @PostUpdate
    public void postUpdate(Object entity) {
        receiveEvent(PostUpdate.class, entity);
    }

    @PrePersist
    public void prePersist(Object entity) {
        receiveEvent(PrePersist.class, entity);
    }

    @PostPersist
    public void postPersist(Object entity) {
        receiveEvent(PostPersist.class, entity);
    }

    @PreRemove
    public void preRemove(Object entity) {
        receiveEvent(PreRemove.class, entity);
    }

    @PostRemove
    public void postRemove(Object entity) {
        receiveEvent(PostRemove.class, entity);
    }

    @PostLoad
    public void postLoad(Object entity) {
        receiveEvent(PostLoad.class, entity);
    }

    private void receiveEvent(Class<? extends Annotation> eventType, Object entity) {
        MyCdiContext.checkAvailable(cdiContext);
        ReceivedEvent.add(ref, new ReceivedEvent(eventType, entity.toString()));
    }
}
