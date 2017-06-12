/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.libs

/**
 * Provides type class BodyReadables and BodyWritables when you import the package object.
 */
package object ws extends DefaultBodyReadables with DefaultBodyWritables
