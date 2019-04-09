/*  NetBare - An android network capture and injection library.
 *  Copyright (C) 2018-2019 Megatron King
 *  Copyright (C) 2018-2019 GuoShi
 *
 *  NetBare is free software: you can redistribute it and/or modify it under the terms
 *  of the GNU General Public License as published by the Free Software Found-
 *  ation, either version 3 of the License, or (at your option) any later version.
 *
 *  NetBare is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 *  PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with NetBare.
 *  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.megatronking.netbare.gateway;

import android.support.annotation.NonNull;

import com.github.megatronking.netbare.NetBareXLog;
import com.github.megatronking.netbare.ip.Protocol;
import com.github.megatronking.netbare.ssl.SSLCodec;
import com.github.megatronking.netbare.ssl.SSLEngineFactory;
import com.github.megatronking.netbare.ssl.SSLRefluxCallback;
import com.github.megatronking.netbare.ssl.SSLRequestCodec;
import com.github.megatronking.netbare.ssl.SSLResponseCodec;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Decodes SSL/TLS packets to plaintext.
 *
 * @author Megatron King
 * @since 2019/4/9 21:39
 */
public class SSLCodecInterceptor<Req extends Request, ReqChain extends AbstractRequestChain<Req, ? extends Interceptor>,
        Res extends Response, ResChain extends AbstractResponseChain<Res, ? extends Interceptor>>
        extends PendingIndexedInterceptor<Req, ReqChain, Res, ResChain>
        implements SSLRefluxCallback<Req, Res> {

    private SSLEngineFactory mEngineFactory;
    private Req mRequest;
    private Res mResponse;

    private SSLRequestCodec mRequestCodec;
    private SSLResponseCodec mResponseCodec;

    private NetBareXLog mLog;

    public SSLCodecInterceptor(SSLEngineFactory engineFactory, Req request, Res response) {
        this.mEngineFactory = engineFactory;
        this.mRequest = request;
        this.mResponse = response;
        mRequestCodec = new SSLRequestCodec(engineFactory);
        mResponseCodec = new SSLResponseCodec(engineFactory);

        mLog = new NetBareXLog(Protocol.TCP, request.ip(), request.port());
    }

    @Override
    protected void intercept(@NonNull ReqChain chain, @NonNull ByteBuffer buffer, int index)
            throws IOException {
        if (mEngineFactory == null) {
            // Skip all interceptors
            chain.processFinal(buffer);
            mLog.w("JSK not installed, skip all interceptors!");
        } else {
            decodeRequest(chain, buffer);
        }
    }

    @Override
    protected void intercept(@NonNull ResChain chain, @NonNull ByteBuffer buffer, int index)
            throws IOException {
        if (mEngineFactory == null) {
            // Skip all interceptors
            chain.processFinal(buffer);
            mLog.w("JSK not installed, skip all interceptors!");
        } else {
            decodeResponse(chain, buffer);
        }
    }

    @Override
    public void onRequest(Req request, ByteBuffer buffer) throws IOException {
        mResponseCodec.encode(buffer, new SSLCodec.CodecCallback() {
            @Override
            public void onPending(ByteBuffer buffer) {
            }

            @Override
            public void onProcess(ByteBuffer buffer) {
            }

            @Override
            public void onEncrypt(ByteBuffer buffer) throws IOException {
                // The encrypt request data is sent to remote server
                mRequest.process(buffer);
            }

            @Override
            public void onDecrypt(ByteBuffer buffer) {
            }
        });
    }

    @Override
    public void onResponse(Res response, ByteBuffer buffer) throws IOException {
        mRequestCodec.encode(buffer, new SSLCodec.CodecCallback() {
            @Override
            public void onPending(ByteBuffer buffer) {
            }

            @Override
            public void onProcess(ByteBuffer buffer) {
            }

            @Override
            public void onEncrypt(ByteBuffer buffer) throws IOException {
                // The encrypt response data is sent to proxy server
                mResponse.process(buffer);
            }

            @Override
            public void onDecrypt(ByteBuffer buffer) {
            }
        });
    }

    private void decodeRequest(final ReqChain chain, ByteBuffer buffer) throws IOException {
        // Merge buffers
        mRequestCodec.decode(mergeRequestBuffer(buffer),
                new SSLCodec.CodecCallback() {
                    @Override
                    public void onPending(ByteBuffer buffer) {
                        pendRequestBuffer(buffer);
                    }

                    @Override
                    public void onProcess(ByteBuffer buffer) throws IOException {
                        chain.processFinal(buffer);
                    }

                    @Override
                    public void onEncrypt(ByteBuffer buffer) throws IOException {
                        mResponse.process(buffer);
                    }

                    @Override
                    public void onDecrypt(ByteBuffer buffer) throws IOException {
                        chain.process(buffer);
                    }
                });
    }


    private void decodeResponse(final ResChain chain, ByteBuffer buffer) throws IOException {
        // Merge buffers
        mResponseCodec.decode(mergeResponseBuffer(buffer),
                new SSLCodec.CodecCallback() {
                    @Override
                    public void onPending(ByteBuffer buffer) {
                        pendResponseBuffer(buffer);
                    }

                    @Override
                    public void onProcess(ByteBuffer buffer) throws IOException {
                        chain.processFinal(buffer);
                    }

                    @Override
                    public void onEncrypt(ByteBuffer buffer) throws IOException {
                        mRequest.process(buffer);
                    }

                    @Override
                    public void onDecrypt(ByteBuffer buffer) throws IOException {
                        chain.process(buffer);
                    }

                });
    }

}