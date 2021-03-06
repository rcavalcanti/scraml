/*
 * (C) Copyright 2015 Atomic BITS (http://atomicbits.io).
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Affero General Public License
 * (AGPL) version 3.0 which accompanies this distribution, and is available in
 * the LICENSE file or at http://www.gnu.org/licenses/agpl-3.0.en.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * Contributors:
 *     Peter Rigole
 *
 */

package io.atomicbits.scraml.parser.model

/**
 * Created by peter on 17/05/15, Atomic BITS bvba (http://atomicbits.io). 
 */
case class Action(actionType: ActionType,
                  headers: Map[String, Parameter],
                  queryParameters: Map[String, Parameter],
                  body: Map[String, MimeType],
                  responses: Map[String, Response])

object Action {

  def apply(action: org.raml.model.Action): Action = {
    val actionType = action.getType match {
      case org.raml.model.ActionType.GET => Get
      case org.raml.model.ActionType.POST => Post
      case org.raml.model.ActionType.PUT => Put
      case org.raml.model.ActionType.DELETE => Delete
      case org.raml.model.ActionType.HEAD => Head
      case org.raml.model.ActionType.OPTIONS => Options
      case org.raml.model.ActionType.PATCH => Patch
      case org.raml.model.ActionType.TRACE => Trace
    }
    val headers: Map[String, Parameter] =
      Transformer.transformMap[org.raml.model.parameter.Header, Parameter](Parameter(_))(action.getHeaders)

    val queryParameters: Map[String, Parameter] =
      Transformer.transformMap[org.raml.model.parameter.QueryParameter, Parameter](Parameter(_))(action.getQueryParameters)

    val body: Map[String, MimeType] =
      Transformer.transformMap[org.raml.model.MimeType, MimeType](MimeType(_))(action.getBody)

    val responses: Map[String, Response] =
      Transformer.transformMap[org.raml.model.Response, Response](Response(_))(action.getResponses)

    Action(actionType, headers, queryParameters, body, responses)
  }

}
