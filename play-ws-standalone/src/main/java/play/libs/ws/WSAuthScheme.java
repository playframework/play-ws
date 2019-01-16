/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package play.libs.ws;

public enum WSAuthScheme {
    DIGEST,
    BASIC,
    NTLM,
    SPNEGO,
    KERBEROS,
    NONE
}
