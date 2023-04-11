/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
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
