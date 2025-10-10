package io.quarkus.it.jpa.entitylistener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.transaction.UserTransaction;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ListAssert;

@Path("/entity-listener")
@ApplicationScoped
public class EntityListenerResource {

    @Inject
    EntityManager em;
    @Inject
    UserTransaction transaction;

    @GET
    @Path("/entity-listeners-annotation-cdi-explicit-scope")
    @Produces(MediaType.TEXT_PLAIN)
    public String entityListenersAnnotationCdiExplicitScope() throws Exception {
        return doTest(EntityWithListenerThroughEntityListenersAnnotationExplicitScope.class,
                entityId -> ReceivedEvent.objectRef(MyListenerRequiringCdiExplicitScope.class, 0),
                EntityWithListenerThroughEntityListenersAnnotationExplicitScope::new,
                EntityWithListenerThroughEntityListenersAnnotationExplicitScope::setId,
                EntityWithListenerThroughEntityListenersAnnotationExplicitScope::setText);
    }

    @GET
    @Path("/entity-listeners-annotation-cdi-implicit-scope")
    @Produces(MediaType.TEXT_PLAIN)
    public String entityListenersAnnotationCdiImplicitScope() throws Exception {
        return doTest(EntityWithListenerThroughEntityListenersAnnotationImplicitScope.class,
                entityId -> ReceivedEvent.objectRef(MyListenerRequiringCdiImplicitScope.class, 0),
                EntityWithListenerThroughEntityListenersAnnotationImplicitScope::new,
                EntityWithListenerThroughEntityListenersAnnotationImplicitScope::setId,
                EntityWithListenerThroughEntityListenersAnnotationImplicitScope::setText);
    }

    @GET
    @Path("/entity-instance-methods")
    @Produces(MediaType.TEXT_PLAIN)
    public String entityInstanceMethods() throws Exception {
        return doTest(EntityWithListenerThroughInstanceMethods.class,
                entityId -> ReceivedEvent.objectRef(EntityWithListenerThroughInstanceMethods.class, entityId),
                EntityWithListenerThroughInstanceMethods::new,
                EntityWithListenerThroughInstanceMethods::setId, EntityWithListenerThroughInstanceMethods::setText);
    }

    private <T> String doTest(Class<T> entityClass, Function<Integer, String> expectedListenerRefFunction,
            Supplier<T> entityConstructor, BiConsumer<T, Integer> entitySetId, BiConsumer<T, String> entitySetText)
            throws Exception {
        ReceivedEvent.clear();

        int entityId = 42;
        String entityRef = ReceivedEvent.objectRef(entityClass, entityId);

        try {
            transaction.begin();
            T entity = entityConstructor.get();
            entitySetId.accept(entity, entityId);
            entitySetText.accept(entity, "initial");
            em.persist(entity);
            transaction.commit();
            String expectedListenerRef = expectedListenerRefFunction.apply(entityId);
            assertThatReceivedEvents(expectedListenerRef)
                    .containsExactly(
                            new ReceivedEvent(PrePersist.class, entityRef),
                            new ReceivedEvent(PostPersist.class, entityRef));

            ReceivedEvent.clear();
            transaction.begin();
            entity = em.find(entityClass, entityId);
            entitySetText.accept(entity, "new");
            transaction.commit();
            expectedListenerRef = expectedListenerRefFunction.apply(entityId);
            assertThatReceivedEvents(expectedListenerRef)
                    .containsExactly(
                            new ReceivedEvent(PostLoad.class, entityRef),
                            new ReceivedEvent(PreUpdate.class, entityRef),
                            new ReceivedEvent(PostUpdate.class, entityRef));

            ReceivedEvent.clear();
            transaction.begin();
            entity = em.find(entityClass, entityId);
            em.remove(entity);
            transaction.commit();
            expectedListenerRef = expectedListenerRefFunction.apply(entityId);
            assertThatReceivedEvents(expectedListenerRef)
                    .containsExactly(
                            new ReceivedEvent(PostLoad.class, entityRef),
                            new ReceivedEvent(PreRemove.class, entityRef),
                            new ReceivedEvent(PostRemove.class, entityRef));
        } catch (Exception | AssertionError e) {
            try {
                transaction.rollback();
            } catch (Throwable t) {
                e.addSuppressed(t);
            }
            throw e;
        }

        return "OK";
    }

    private ListAssert<Object> assertThatReceivedEvents(String listenerRef) {
        return assertThat(ReceivedEvent.get())
                .as("Received events")
                .containsOnlyKeys(listenerRef)
                .extractingByKey(listenerRef, InstanceOfAssertFactories.LIST)
                .as("Received events for " + listenerRef);
    }
}
