/*
 *
 *  (C) Copyright 2015 Atomic BITS (http://atomicbits.io).
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the GNU Affero General Public License
 *  (AGPL) version 3.0 which accompanies this distribution, and is available in
 *  the LICENSE file or at http://www.gnu.org/licenses/agpl-3.0.en.html
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Affero General Public License for more details.
 *
 *  Contributors:
 *      Peter Rigole
 *
 */

package io.atomicbits.scraml.generator

import java.io.File

import io.atomicbits.scraml.generator.lookup.{SchemaLookupParser, SchemaLookup}
import io.atomicbits.scraml.jsonschemaparser.model.Schema
import io.atomicbits.scraml.jsonschemaparser.JsonSchemaParser
import org.raml.parser.rule.ValidationResult

import io.atomicbits.scraml.parser._
import io.atomicbits.scraml.parser.model._
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

// What we need is:
// http://stackoverflow.com/questions/21515325/add-a-compile-time-only-dependency-in-sbt

object ScramlGenerator {

  private val logger = LoggerFactory.getLogger(this.getClass)

  // Macro annotations must be whitebox. If you declare a macro annotation as blackbox, it will not work.
  // See: http://docs.scala-lang.org/overviews/macros/annotations.html
  def generate(ramlApiPath: String, apiPackageName: String, apiClassName: String): Seq[(File, String)] = {

    // Validate RAML spec
    logger.info(s"Running RAML validation on $ramlApiPath: ")
    val validationResults: List[ValidationResult] = RamlParser.validateRaml(ramlApiPath)
    //      Try(RamlParser.validateRaml(ramlApiPath)) match {
    //        case Success(validResults) => validResults
    //        case Failure(e: NullPointerException) =>
    //          throw new IllegalArgumentException(s"RAML validation failed, likely because the api path '$ramlApiPath' could not be not found.", e)
    //        case Failure(e) => throw e
    //      }
    if (validationResults.nonEmpty) {
      sys.error(
        s"""
           |Invalid RAML specification:
           |
           |${RamlParser.printValidations(validationResults)}
            |
            |""".stripMargin
      )
    }
    logger.info("RAML model is valid")

    // Generate the RAML model
    logger.info("Running RAML model generation")
    val raml: Raml = RamlParser.buildRaml(ramlApiPath).asScala
    logger.info(s"RAML model generated")

    val schemas: Map[String, Schema] = JsonSchemaParser.parse(raml.schemas)
    val schemaLookup: SchemaLookup = SchemaLookupParser.parse(schemas)
    logger.info(s"Schema Lookup generated")

    val caseClasses: List[String] = CaseClassGenerator.generateCaseClasses(schemaLookup)
    logger.info(s"Case classes generated")

    val resources: List[String] = raml.resources.map(resource => ResourceExpander.expandResource(resource, schemaLookup))
    logger.info(s"Resources DSL generated")

    // ToDo: process enumerations
    //    val enumObjects = CaseClassGenerator.generateEnumerationObjects(schemaLookup, c)

    // rewrite the class definition

    val classDefinition =
      s"""
         |package $apiPackageName
          |
          |case class $apiClassName(host: String,
                                     |                       port: Int = 80,
                                     |                       protocol: String = "http",
                                     |                       prefix: Option[String] = None,
                                     |                       requestTimeout: Int = 5000,
                                     |                       maxConnections: Int = 2,
                                     |                       defaultHeaders: Map[String, String] = Map.empty) {
                                     |
                                     |import io.atomicbits.scraml.dsl._
                                     |import io.atomicbits.scraml.dsl.client.rxhttpclient.RxHttpClient
                                     |
                                     |import play.api.libs.json._
                                     |
                                     |import $apiClassName._
                                                            |
                                                            |protected val requestBuilder = RequestBuilder(new RxHttpClient(protocol, host, port, prefix, requestTimeout, maxConnections, defaultHeaders))
                                                            |
                                                            |def close() = requestBuilder.client.close()
                                                            |
                                                            | ${resources.mkString("\n")}
          |
          |}
          |
          |object $apiClassName {
                                 |
                                 |import play.api.libs.json._
                                 |import scala.concurrent.Future
                                 |import io.atomicbits.scraml.dsl.Response
                                 |import scala.concurrent.ExecutionContext.Implicits.global
                                 |
                                 |implicit class FutureResponseOps[T](val futureResponse: Future[Response[T]]) extends AnyVal {
                                 |
                                 |  def asString: Future[String] = futureResponse.map(_.stringBody)
                                 |
                                 |  def asJson: Future[JsValue] =
                                 |    futureResponse.map { resp =>
                                 |      resp.jsonBody.getOrElse {
                                 |        val message =
                                 |          if (resp.status != 200)
                                 |            "The response has no JSON body because the request was not successful (status = " + resp.status + ")."
                                 |          else "The response has no JSON body despite status 200."
                                 |        throw new IllegalArgumentException(message)
                                 |      }
                                 |    }
                                 |
                                 |  def asType: Future[T] =
                                 |    futureResponse.map { resp =>
                                 |      resp.body.getOrElse {
                                 |        val message =
                                 |          if (resp.status != 200)
                                 |            "The response has no typed body because the request was not successful (status = " + resp.status + ")."
                                 |          else "The response has no typed body despite status 200."
                                 |        throw new IllegalArgumentException(message)
                                 |      }
                                 |    }
                                 |}
                                 |
                                 | ${caseClasses.mkString("\n")}
          |
          |}
     """.stripMargin

    val pathParts: Array[String] = apiPackageName.split('.')
    val dir = pathParts.tail.foldLeft(new File(pathParts.head))((file, pathPart) => new File(file, pathPart))
    val file = new File(dir, s"$apiClassName.scala")

    Seq((file, classDefinition))
  }

}
