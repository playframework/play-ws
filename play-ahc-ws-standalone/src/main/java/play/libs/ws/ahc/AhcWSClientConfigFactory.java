/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 *
 */

package play.libs.ws.ahc;

import com.typesafe.config.Config;
import play.api.libs.ws.WSClientConfig;
import play.api.libs.ws.ahc.AhcWSClientConfig;

/**
 * This is a factory that provides AhcWSClientConfig
 * configuration objects without having to go through individual parsers
 * and so forth individually.
 */
public final class AhcWSClientConfigFactory {

    /**
     * Creates a AhcWSClientConfig from a Typesafe Config object.
     *
     * This is intended to be called from Java API.
     *
     * @param config the config file containing settings for WSConfigParser
     * @param classLoader the classloader
     * @return a AhcWSClientConfig configuration object.
     */
    public static AhcWSClientConfig forConfig(Config config, ClassLoader classLoader)  {
        return play.api.libs.ws.ahc.AhcWSClientConfigFactory$.MODULE$.forConfig(config, classLoader);
    }

    /**
     * Creates a AhcWSClientConfig with defaults from a WSClientConfig configuration object.
     *
     * @param config the basic WSClientConfig configuration object.
     * @return
     */
    public static AhcWSClientConfig forClientConfig(WSClientConfig config) {
        return play.api.libs.ws.ahc.AhcWSClientConfigFactory$.MODULE$.forClientConfig(config);
    }
}
