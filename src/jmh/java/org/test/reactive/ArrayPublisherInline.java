package org.test.reactive;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Create Publisher that sends elements of a given array to each new subscriber
 * <p>
 * Acceptance Criteria: As a developer
 * I want to subscribe to the ArrayPublisher
 * So by doing that, receive elements of that publisher
 *
 * @param <T>
 */
public class ArrayPublisherInline<T> implements Publisher<T> {

    private final T[] array;

    public ArrayPublisherInline(T[] array) {
        this.array = array;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new ArraySubscription<>(array, subscriber));

    }

    private static class ArraySubscription<T> implements Subscription {

        private final Subscriber<? super T> subscriber;

        private final T[] array;

        int index;

        volatile boolean canceled;

        volatile long requested;
        static final AtomicLongFieldUpdater<ArraySubscription> REQUESTED =
                AtomicLongFieldUpdater.newUpdater(ArraySubscription.class, "requested");

        public ArraySubscription(T[] array, Subscriber<? super T> subscriber) {
            this.array = array;
            this.subscriber = subscriber;
        }


        @Override
        public void request(long n) {

            if (n <= 0) {
                return;
            }

            long initialRequested;

            for (;;) {
                initialRequested = REQUESTED.get(this);

                if (initialRequested == Long.MAX_VALUE) {
                    return;
                }

                n = initialRequested + n;

                if (n <= 0) {
                    n = Long.MAX_VALUE;
                }

                if (REQUESTED.compareAndSet(this, initialRequested, n)) {
                    break;
                }
            }

            if (initialRequested > 0) {
                return;
            }

            if (n > (array.length - index)) {
                fastPath();
            } else {
                slowPath(n);
            }

        }

        void slowPath(long n) {
            int sent = 0;
            int idx = this.index;
            Subscriber<? super T> subscriber = this.subscriber;

            while (true) {
                for (; sent < n && idx < this.array.length; sent++, idx++) {
                    if (canceled) {
                        return;
                    }

                    T element = this.array[idx];

                    if (element == null) {
                        subscriber.onError(new NullPointerException());
                        return;
                    }

                    subscriber.onNext(element);
                }

                if (canceled) {
                    return;
                }

                if (idx == this.array.length) {
                    subscriber.onComplete();
                    return;
                }

                n = requested;

                if (n == sent) {
                    index = idx;
                    n = REQUESTED.addAndGet(this, -sent);
                    if (n == 0) {
                        return;
                    }
                }

                sent = 0;
            }
        }

        void fastPath() {
            int idx = this.index;
            Subscriber<? super T> subscriber = this.subscriber;

            for (; idx < this.array.length; idx++) {
                if (canceled) {
                    return;
                }

                T element = this.array[idx];

                if (element == null) {
                    subscriber.onError(new NullPointerException());
                    return;
                }

                subscriber.onNext(element);
            }

            if (canceled) {
                return;
            }

            subscriber.onComplete();
        }

        @Override
        public void cancel() {
            canceled = true;
        }
    }
}
