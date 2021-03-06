/*
 * Copyright 2016 Artem Labazin <xxlabaza@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.form;

import static feign.Util.UTF_8;

import feign.RequestTemplate;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLConnection;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.val;

/**
 * Multipart form data implementation of {@link feign.form.FormDataProcessor}.
 *
 * @author Artem Labazin <xxlabaza@gmail.com>
 * @since 30.04.2016
 */
public class MultipartEncodedDataProcessor implements FormDataProcessor {

    public static final String CONTENT_TYPE;

    private static final String CRLF;

    static {
        CONTENT_TYPE = "multipart/form-data";
        CRLF = "\r\n";
    }

    @Override
    @SneakyThrows
    public void process (Map<String, Object> data, RequestTemplate template) {
        val boundary = createBoundary();
        val outputStream = new ByteArrayOutputStream();

        try {
            val writer = new PrintWriter(outputStream);
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                writer.append("--" + boundary).append(CRLF);
                if (isFile(entry.getValue())) {
                    writeFile(outputStream, writer, entry.getKey(), entry.getValue());
                } else {
                    writeParameter(writer, entry.getKey(), entry.getValue().toString());
                }
                writer.append(CRLF).flush();
            }

            writer.append("--" + boundary + "--").append(CRLF).flush();
        } catch (Throwable throwable) {
            try {
                outputStream.close();
            } catch (IOException ex) {
            }
            throw throwable;
        }

        val contentType = new StringBuilder()
                .append(CONTENT_TYPE)
                .append("; boundary=")
                .append(boundary)
                .toString();

        template.header("Content-Type", contentType);
        template.body(outputStream.toByteArray(), UTF_8);
        outputStream.close();
    }

    @Override
    public String getSupportetContentType () {
        return CONTENT_TYPE;
    }

    /**
     * Checks is passed object a supported file's type or not.
     *
     * @param value form file parameter.
     */
    protected boolean isFile (Object value) {
        return value != null && (value instanceof File || value instanceof byte[]);
    }

    /**
     * Writes file's content to output stream.
     *
     * @param output output stream to remote destination.
     * @param writer wrapped output stream.
     * @param name file's name.
     * @param value file's content.
     */
    protected void writeFile (OutputStream output, PrintWriter writer, String name, Object value) {
        if (value instanceof byte[]) {
            writeFile(output, writer, name, (byte[]) value);
            return;
        }
        writeFile(output, writer, name, (File) value);
    }

    private String createBoundary () {
        return Long.toHexString(System.currentTimeMillis());
    }

    private void writeParameter (PrintWriter writer, String name, String value) {
        writer.append("Content-Disposition: form-data; name=\"" + name + "\"").append(CRLF);
        writer.append("Content-Type: text/plain; charset=UTF-8").append(CRLF);
        writer.append(CRLF).append(value);
    }

    @SneakyThrows
    private void writeFile (OutputStream output, PrintWriter writer, String name, File file) {
        writeFileMeta(writer, name, file.getName());

        InputStream input = null;
        try {
            input = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        } finally {
            if (input != null) {
                input.close();
            }
        }
        writer.flush();
    }

    @SneakyThrows
    private void writeFile (OutputStream output, PrintWriter writer, String name, byte[] bytes) {
        writeFileMeta(writer, name, "");
        output.write(bytes);
        writer.flush();
    }

    private void writeFileMeta (PrintWriter writer, String name, String fileName) {
        val contentDesposition = new StringBuilder()
                .append("Content-Disposition: form-data; name=\"").append(name).append("\"; ")
                .append("filename=\"").append(fileName).append("\"")
                .toString();
        val contentType = new StringBuilder()
                .append("Content-Type: ")
                .append(URLConnection.guessContentTypeFromName(fileName))
                .toString();

        writer.append(contentDesposition).append(CRLF);
        writer.append(contentType).append(CRLF);
        writer.append("Content-Transfer-Encoding: binary").append(CRLF);
        writer.append(CRLF).flush();
    }
}
