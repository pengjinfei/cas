/*
  $Id: $

  Copyright (C) 2012 Virginia Tech.
  All rights reserved.

  SEE LICENSE FOR MORE INFORMATION

  Author:  Middleware Services
  Email:   middleware@vt.edu
  Version: $Revision: $
  Updated: $Date: $
*/
package org.jasig.cas.ticket.registry.support.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.SerializationException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.serialize.DateSerializer;
import com.esotericsoftware.kryo.serialize.FieldSerializer;
import net.spy.memcached.CachedData;
import net.spy.memcached.transcoders.Transcoder;
import org.jasig.cas.authentication.ImmutableAuthentication;
import org.jasig.cas.authentication.MutableAuthentication;
import org.jasig.cas.authentication.principal.SamlService;
import org.jasig.cas.authentication.principal.SimplePrincipal;
import org.jasig.cas.authentication.principal.SimpleWebApplicationServiceImpl;
import org.jasig.cas.ticket.ServiceTicketImpl;
import org.jasig.cas.ticket.TicketGrantingTicketImpl;
import org.jasig.cas.ticket.registry.support.kryo.serial.*;
import org.jasig.cas.ticket.support.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link net.spy.memcached.MemcachedClient} transcoder implementation based on Kryo fast serialization framework
 * suited for efficient serialization of tickets.
 *
 * @author Middleware Services
 * @version $Revision: $
 */
public class KryoTranscoder implements Transcoder<Object> {

    /** Ticket granting ticket type flag. */
    public static int TGT_TYPE = 314159265;

    /** Service ticket type flag. */
    public static int ST_TYPE = 271828183;

    /** Kryo serializer */
    private final Kryo kryo = new Kryo();

    /** Logging instance. */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /** Maximum size of single encoded object in bytes. */
    private final int bufferSize;

    /** Field reflection helper class. */
    private final FieldHelper fieldHelper = new FieldHelper();

    /** Map of class to serializer that handles it. */
    private Map<Class<?>, Serializer> serializerMap;


    /**
     * Creates a Kryo-based transcoder.
     *
     * @param initialBufferSize Initial size for buffer holding encoded object data.
     */
    public KryoTranscoder(final int initialBufferSize) {
        bufferSize = initialBufferSize;
    }


    /**
     * Sets a map of additional types that should be regisetered with Kryo,
     * for example GoogleAccountsService and OpenIdService.
     *
     * @param map Map of class to the serializer instance that handles it.
     */
    public void setSerializerMap(final Map<Class<?>, Serializer> map) {
        this.serializerMap = map;
    }

    public void initialize() {
        // Register types we know about and do not require external configuration
        kryo.register(ArrayList.class);
        kryo.register(Date.class, new DateSerializer());
        kryo.register(HardTimeoutExpirationPolicy.class, new HardTimeoutExpirationPolicySerializer(kryo, fieldHelper));
        kryo.register(HashMap.class);
        kryo.register(ImmutableAuthentication.class, new ImmutableAuthenticationSerializer(kryo, fieldHelper));
        kryo.register(
                MultiTimeUseOrTimeoutExpirationPolicy.class,
                new MultiTimeUseOrTimeoutExpirationPolicySerializer(kryo, fieldHelper));
        kryo.register(MutableAuthentication.class, new MutableAuthenticationSerializer(kryo, fieldHelper));
        kryo.register(
                NeverExpiresExpirationPolicy.class,
                new FieldSerializer(kryo, NeverExpiresExpirationPolicy.class));
        kryo.register(
                RememberMeDelegatingExpirationPolicy.class,
                new FieldSerializer(kryo, RememberMeDelegatingExpirationPolicy.class));
        kryo.register(SamlService.class, new SamlServiceSerializer(kryo, fieldHelper));
        kryo.register(ServiceTicketImpl.class);
        kryo.register(SimplePrincipal.class, new SimplePrincipalSerializer(kryo));
        kryo.register(SimpleWebApplicationServiceImpl.class, new SimpleWebApplicationServiceSerializer(kryo));
        kryo.register(TicketGrantingTicketImpl.class);
        kryo.register(
                ThrottledUseAndTimeoutExpirationPolicy.class,
                new FieldSerializer(kryo, ThrottledUseAndTimeoutExpirationPolicy.class));
        kryo.register(
                TicketGrantingTicketExpirationPolicy.class,
                new FieldSerializer(kryo, TicketGrantingTicketExpirationPolicy.class));
        kryo.register(TimeoutExpirationPolicy.class, new TimeoutExpirationPolicySerializer(kryo, fieldHelper));

        // Register other types
        if (serializerMap != null) {
            for (Class<?> clazz : serializerMap.keySet()) {
                kryo.register(clazz, serializerMap.get(clazz));
            }
        }

        // Catchall for any classes not explicitly registered
        kryo.setRegistrationOptional(true);
    }


    /**
     * Asynchronous decoding is not supported.
     *
     * @param d Data to decode.
     * @return False.
     */
    public boolean asyncDecode(CachedData d) {
        return false;
    }


    /** {@inheritDoc} */
    public CachedData encode(final Object o) {
        final byte[] bytes = encodeToBytes(o);
        return new CachedData(0, bytes, bytes.length);
    }


    /** {@inheritDoc} */
    public Object decode(final CachedData d) {
        return kryo.readClassAndObject(ByteBuffer.wrap(d.getData()));
    }


    /**
     * Maximum size of encoded data supported by this transcoder.
     *
     * @return  {@link CachedData.MAX_SIZE}
     */
    public int getMaxSize() {
        return CachedData.MAX_SIZE;
    }


    /**
     * Gets the kryo object that provides encoding and decoding services for this instance.
     *
     * @return Underlying Kryo instance.
     */
    public Kryo getKryo() {
        return kryo;
    }


    /**
     * Encodes the given object using registered Kryo serializers.  Provides explicit buffer overflow protection, but
     * careful buffer sizing should be employed to reduce the need for this facility.
     *
     * @param o Object to encode.
     *
     * @return Encoded bytes.
     */
    private byte[] encodeToBytes(final Object o) {
        int factor = 1;
        byte[] result = null;
        ByteBuffer buffer = Kryo.getContext().getBuffer(bufferSize * factor);
        while (result == null) {
            try {
                kryo.writeClassAndObject(buffer, o);
                result = new byte[buffer.flip().limit()];
                buffer.get(result);
            } catch (SerializationException e) {
                Throwable rootCause = e;
                while (rootCause.getCause() != null) {
                    rootCause = rootCause.getCause();
                }
                if (rootCause instanceof BufferOverflowException) {
                    buffer = ByteBuffer.allocate(bufferSize * ++factor);
                    logger.warn("Buffer overflow while encoding " + o);
                } else {
                    throw e;
                }
            }
        }
        return result;
    }
}
