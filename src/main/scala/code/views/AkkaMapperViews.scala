package code.views

import code.model.{CreateViewJSON, Permission, UpdateViewJSON, _}
import net.liftweb.common._

import scala.collection.immutable.List
import code.model._
import com.typesafe.config.ConfigFactory
import net.liftweb.common.Full
import net.liftweb.util.Props

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import akka.actor.{ActorKilledException, ActorSelection, ActorSystem}
import akka.util.Timeout
import code.api.APIFailure
import code.model.dataAccess.ResourceUser
import code.users.{RemoteUserCaseClasses, Users}
import code.metadata.counterparties.{Counterparties, CounterpartyTrait, RemoteCounterpartiesCaseClasses}


object AkkaMapperViews extends Views with Users with Counterparties{

  val TIMEOUT = 10 seconds
  val r = RemoteViewCaseClasses
  val ru = RemoteUserCaseClasses
  val rCounterparties = RemoteCounterpartiesCaseClasses
  implicit val timeout = Timeout(10000 milliseconds)

  val remote = ActorSystem("LookupSystem", ConfigFactory.load("remotelookup"))
  val cfg = ConfigFactory.load("obplocaldata")
  val host = cfg.getString("akka.remote.netty.tcp.hostname")
  val port = cfg.getString("akka.remote.netty.tcp.port")
  var actorPath = "akka.tcp://OBPDataWorkerSystem@" + host + ":" + port + "/user/OBPLocalDataActor"
  if (Props.getBool("enable_remotedata", false)) {
    val cfg = ConfigFactory.load("obpremotedata")
    val rhost = cfg.getString("akka.remote.netty.tcp.hostname")
    val rport = cfg.getString("akka.remote.netty.tcp.port")
    actorPath = "akka.tcp://OBPDataWorkerSystem@" + rhost + ":" + rport + "/user/OBPRemoteDataActor"
  }

  var viewsActor: ActorSelection = remote.actorSelection(actorPath)

  def addPermissions(views: List[ViewUID], user: User): Box[List[View]] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? r.addPermissions(views, user)).mapTo[List[View]],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"One or more views not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def permission(account: BankAccount, user: User): Box[Permission] = {
    Full(
      Await.result(
        (viewsActor ? r.permission(account, user)).mapTo[Permission],
        TIMEOUT
      )
    )
  }

  def addPermission(viewUID: ViewUID, user: User): Box[View] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? r.addPermission(viewUID, user)).mapTo[View],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"View $viewUID. not found", 404)
      case e: Throwable => throw e
    }
    res

  }

  def revokePermission(viewUID : ViewUID, user : User) : Box[Boolean] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? r.revokePermission(viewUID, user)).mapTo[Boolean],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException => return Empty ~> APIFailure(s"View $viewUID. not found", 404)
      case e: Throwable => throw e
    }

    if ( res.getOrElse(false) ) {
      res
    }
    else
      Empty ~> Failure("access cannot be revoked")
  }

  def revokeAllPermissions(bankId : BankId, accountId: AccountId, user : User) : Box[Boolean] = {
    val res = try{
      Full(
        Await.result(
          (viewsActor ? r.revokeAllPermissions(bankId, accountId, user)).mapTo[Boolean],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException => Empty ~> Failure("One of the views this user has access to is the owner view, and there would be no one with access" +
          " to this owner view if access to the user was revoked. No permissions to any views on the account have been revoked.")

      case e: Throwable => throw e
    }
    res
  }

  def view(viewUID : ViewUID) : Box[View] = {
    val res = try {
      Full(
      Await.result(
        (viewsActor ? r.view(viewUID)).mapTo[View],
        TIMEOUT
      )
    )
  }
  catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"View $viewUID. not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def view(viewId : ViewId, account: BankAccount) : Box[View] = {
    Await.result(
      (viewsActor ? r.view(viewId, account)).mapTo[Box[View]],
      TIMEOUT
    )
  }

  def createView(bankAccount: BankAccount, view: CreateViewJSON): Box[View] = {
    Await.result(
      (viewsActor ? r.createView(bankAccount, view)).mapTo[Box[View]],
      TIMEOUT
    )
  }

  def updateView(bankAccount : BankAccount, viewId: ViewId, viewUpdateJson : UpdateViewJSON) : Box[View] = {
    Await.result(
      (viewsActor ? r.updateView(bankAccount, viewId, viewUpdateJson)).mapTo[Box[View]],
      TIMEOUT
    )
  }

  def removeView(viewId: ViewId, bankAccount: BankAccount): Box[Unit] = {
    Await.result(
      (viewsActor ? r.removeView(viewId, bankAccount)).mapTo[Box[Unit]],
      TIMEOUT
    )
  }

  def permissions(account : BankAccount) : List[Permission] = {
    Await.result(
      (viewsActor ? r.permissions(account)).mapTo[List[Permission]],
      TIMEOUT
    )
  }

  def views(bankAccount : BankAccount) : List[View] = {
    Await.result(
      (viewsActor ? r.views(bankAccount)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def permittedViews(user: User, bankAccount: BankAccount): List[View] = {
    Await.result(
      (viewsActor ? r.permittedViews(user, bankAccount)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def publicViews(bankAccount : BankAccount) : List[View] = {
    Await.result(
      (viewsActor ? r.publicViews(bankAccount)).mapTo[List[View]],
      TIMEOUT
    )
  }

  def getAllPublicAccounts() : List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getAllPublicAccounts()).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

  def getPublicBankAccounts(bank : Bank) : List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getPublicBankAccounts(bank)).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

  def getAllAccountsUserCanSee(user : Box[User]) : List[BankAccount] = {
    user match {
      case Full(theUser) => {
        Await.result (
          (viewsActor ? r.getAllAccountsUserCanSee(theUser)).mapTo[List[BankAccount]],
          TIMEOUT)
      }
      case _ => getAllPublicAccounts()
    }
  }

  def getAllAccountsUserCanSee(bank: Bank, user : Box[User]) : List[BankAccount] = {
    user match {
      case Full(theUser) => {
        Await.result(
          (viewsActor ? r.getAllAccountsUserCanSee(bank, theUser)).mapTo[List[BankAccount]],
          TIMEOUT
        )
      }
      case _ => getPublicBankAccounts(bank)
    }
  }

  def getNonPublicBankAccounts(user : User) :  List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getNonPublicBankAccounts(user)).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

  def getNonPublicBankAccounts(user : User, bankId : BankId) :  List[BankAccount] = {
    Await.result(
      (viewsActor ? r.getNonPublicBankAccounts(user, bankId)).mapTo[List[BankAccount]],
      TIMEOUT
    )
  }

  def grantAccessToAllExistingViews(user : User) = {
    Await.result(
      (viewsActor ? r.grantAccessToAllExistingViews(user)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def grantAccessToView(user : User, view : View) = {
    Await.result(
      (viewsActor ? r.grantAccessToView(user, view)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def createOwnerView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? r.createOwnerView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createPublicView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? r.createPublicView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createAccountantsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? r.createAccountantsView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createAuditorsView(bankId: BankId, accountId: AccountId, description: String) : Box[View] = {
    Full(Await.result(
      (viewsActor ? r.createAuditorsView(bankId, accountId, description)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def createRandomView(bankId: BankId, accountId: AccountId) : Box[View] = {
    Full(Await.result(
      (viewsActor ? r.createRandomView(bankId, accountId)).mapTo[View],
      TIMEOUT
      )
    )
  }

  def viewExists(bankId: BankId, accountId: AccountId, name: String): Boolean = {
    Await.result(
      (viewsActor ? r.viewExists(bankId, accountId, name)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def removeAllViews(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (viewsActor ? r.removeAllViews(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def removeAllPermissions(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (viewsActor ? r.removeAllViews(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }
  // Resource user part
  def getUserByResourceUserId(id : Long) : Box[User] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.getUserByResourceUserId(id)).mapTo[User],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def getUserByProviderId(provider : String, idGivenByProvider : String) : Box[User] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.getUserByProviderId(provider, idGivenByProvider)).mapTo[User],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not found", 404)
      case e: Throwable => throw e
    }
    res
  }

  def getUserByUserId(userId : String) : Box[User] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.getUserByUserId(userId)).mapTo[User],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not found", 404)
      case e: Throwable => throw e
    }
    res
  }
  def getUserByUserName(userName : String) : Box[ResourceUser] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.getUserByUserName(userName)).mapTo[ResourceUser],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not found", 404)
      case e: Throwable => throw e
    }
    res
  }
  def getUserByEmail(email : String) : Box[List[ResourceUser]] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.getUserByEmail(email)).mapTo[List[ResourceUser]],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not found", 404)
      case e: Throwable => throw e
    }
    res
  }
  def getAllUsers() : Box[List[ResourceUser]] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.getAllUsers()).mapTo[List[ResourceUser]],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Users not found", 404)
      case e: Throwable => throw e
    }
    res
  }
  def createResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.createResourceUser(provider, providerId, name, email, userId)).mapTo[ResourceUser],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not created", 404)
      case e: Throwable => throw e
    }
    res
  }
  def createUnsavedResourceUser(provider: String, providerId: Option[String], name: Option[String], email: Option[String], userId: Option[String]) : Box[ResourceUser] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.createUnsavedResourceUser(provider, providerId, name, email, userId)).mapTo[ResourceUser],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not created", 404)
      case e: Throwable => throw e
    }
    res
  }
  def saveResourceUser(resourceUser: ResourceUser) : Box[ResourceUser] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? ru.saveResourceUser(resourceUser)).mapTo[ResourceUser],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"User not created", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty): Box[CounterpartyMetadata] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.getOrCreateMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, otherParty: Counterparty)).mapTo[CounterpartyMetadata],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getOrCreateMetadata", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId): List[CounterpartyMetadata] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId)).mapTo[List[CounterpartyMetadata]],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getMetadatas", 404)
      case e: Throwable => throw e
    }
    res.get
  }

  override def getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[CounterpartyMetadata] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String)).mapTo[CounterpartyMetadata],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getMetadata", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def getCounterparty(counterPartyId: String): Box[CounterpartyTrait] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.getCounterparty(counterPartyId: String)).mapTo[CounterpartyTrait],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getCounterparty", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.getCounterpartyByIban(iban: String)).mapTo[CounterpartyTrait],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not getCounterpartyByIban", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def createCounterparty(createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, name: String, otherBankId: String, otherAccountId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, isBeneficiary: Boolean): Box[CounterpartyTrait] = {
    val res = try {
      Full(
        Await.result(
          (viewsActor ? rCounterparties.createCounterparty(createdByUserId, thisBankId, thisAccountId, thisViewId, name, otherBankId, otherAccountId,
                                                           otherAccountRoutingScheme, otherAccountRoutingAddress, otherBankRoutingScheme, otherBankRoutingAddress,
                                                           isBeneficiary)).mapTo[CounterpartyTrait],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Can not  createCounterparty", 404)
      case e: Throwable => throw e
    }
    res
  }

  override def checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String): Boolean = {
    Await.result(
      (viewsActor ? rCounterparties.checkCounterpartyAvailable(name: String, thisBankId: String, thisAccountId: String, thisViewId: String)).mapTo[Boolean],
      TIMEOUT
    )
  }
}

