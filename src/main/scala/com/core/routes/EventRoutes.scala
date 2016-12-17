package com.core.routes

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import com.core.persistence.EventPersistenceActor.{AddEvent, EditEvent, GetAllEvents, GetEvent}
import com.core_api.dto.EventJsonProtocol._
import com.core_api.dto.{Event, EventDto}

import scala.util.{Failure, Success}
import scala.concurrent.duration._

case class EventRoutes(eventPersistenceActor: ActorRef) {

  implicit val timeout: Timeout = 3 seconds
  var list = List[EventDto]()

  val route =
    path("events") {
      post {
        entity(as[Event]) { event =>
          onComplete((eventPersistenceActor ? AddEvent(event)).mapTo[Long]) {
            case Success(eventId) => complete(Created -> eventId.toString)
            case Failure(ex) => complete(NotFound -> ex.getMessage)
          }
        }
      } ~
      get {
        onComplete((eventPersistenceActor ? GetAllEvents).mapTo[Seq[EventDto]]) {
          case Success(events) => complete(OK -> events)
          case Failure(ex) => complete(NotFound -> ex.getMessage)
        }
      }
    } ~
    path("events" / LongNumber) { eventId =>
      get {
        onComplete((eventPersistenceActor ? GetEvent(eventId)).mapTo[Option[EventDto]]) {
          case Success(eventDtoOption) => eventDtoOption match {
            case Some(eventDto) => complete(eventDto)
            case None => complete(NotFound -> "The event does not exist")
          }
          case Failure(ex) => complete(InternalServerError -> ex.getMessage)
        }
      } ~
      put {
        entity(as[Event]) { event =>
          val eventDto = event.dto(eventId)
          onComplete((eventPersistenceActor ? EditEvent(event.dto(eventId))).mapTo[Option[EventDto]]) {
            case Success(eventDtoOption) => eventDtoOption match {
              case Some(theEventDto) =>
                complete(theEventDto)
              case None =>
                complete(NotFound -> "The event meant to update does not exist")
            }
            case Failure(ex) => complete(InternalServerError -> ex.getMessage)
          }
        }
      } ~
      delete {
        list.find(_.id == eventId) match {
          case Some(_) =>
            list = list.filterNot(_.id == eventId)
            complete(NoContent)
          case None =>
            complete(NotFound)
        }
      }
    }
}
