package com.testdatagen.config;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.serializer.ValueFilter;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class FastjsonConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();

        FastJsonConfig config = new FastJsonConfig();
        config.setSerializerFeatures(
                SerializerFeature.PrettyFormat,
                SerializerFeature.WriteMapNullValue,
                SerializerFeature.DisableCircularReferenceDetect
        );
        config.setCharset(StandardCharsets.UTF_8);

        // 超出 JavaScript Number.MAX_SAFE_INTEGER 的 Long 值序列化为字符串，避免前端精度丢失
        ValueFilter bigIntFilter = (object, name, value) -> {
            if (value instanceof Long) {
                long v = (Long) value;
                if (v > 9007199254740991L || v < -9007199254740991L) {
                    return String.valueOf(v);
                }
            }
            return value;
        };
        config.setSerializeFilters(bigIntFilter);

        List<MediaType> mediaTypes = new ArrayList<>();
        mediaTypes.add(MediaType.APPLICATION_JSON);
        mediaTypes.add(new MediaType("application", "json", StandardCharsets.UTF_8));

        converter.setSupportedMediaTypes(mediaTypes);
        converter.setFastJsonConfig(config);

        // 添加到最前面，优先使用
        converters.add(0, converter);
    }
}
