package com.example.playwsclient

import org.scalatest.{Matchers, PropSpec}
import org.scalatest.prop._

class CheckSpec
  extends PropSpec
  with GeneratorDrivenPropertyChecks
  with Matchers {

  property ("Addition and multiplication are related") {
    forAll { (x: Int) =>
      whenever(x > 0) {
        x * 2 should be(x + x)
      }
    }
  }

}
