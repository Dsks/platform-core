package app.platformcore.apiusers.application.port.in;

import app.platformcore.apiusers.application.exception.EmailAlreadyInUseException;
import app.platformcore.apiusers.application.exception.ForbiddenOperationException;
import app.platformcore.apiusers.application.exception.InvalidCommandException;
import app.platformcore.apiusers.application.exception.RoleNotFoundException;
import app.platformcore.apiusers.domain.model.UserId;
import java.util.Set;

/**
 * Defines the application boundary for administrative user creation.
 *
 * <p>This port is intended for inbound adapters such as web controllers, internal jobs, consumers,
 * or application tests that already know the actor context. Implementations are responsible for
 * enforcing user uniqueness, role assignment policy, password hashing, persistence orchestration,
 * and publication of the resulting application event. Transport concerns such as HTTP
 * authentication, JSON parsing, request validation annotations, response shaping, and concrete
 * repository technology stay outside this contract.
 *
 * <p>Commands are expected to carry a valid email address, a non-null plaintext password, and
 * optional business role names. Inbound adapters must avoid logging sensitive command values in
 * clear text.
 */
public interface CreateUserUseCase {

  /**
   * Carries the data required to create a user from an administrative entry point.
   *
   * @param email personal data used as the user's login identifier; adapters should redact it in
   *     logs unless there is a controlled diagnostic need
   * @param rawPassword plaintext credential material that must only be passed to the application
   *     boundary and never logged or stored as-is
   * @param roles requested business roles for the new account; role names are authorization data
   *     and should be treated as controlled operational information
   */
  record Command(String email, String rawPassword, Set<String> roles) {}

  /**
   * Identifies the user aggregate created by this use case.
   *
   * @param id stable user identifier returned after the aggregate has been persisted
   */
  record Result(UserId id) {}

  /**
   * Creates a user account and assigns the requested or default roles according to application
   * policy.
   *
   * @param command creation data supplied by an inbound adapter; the command itself, email, and raw
   *     password must be present
   * @return the identifier of the newly created user
   * @throws InvalidCommandException when required command data is missing
   * @throws EmailAlreadyInUseException when this flow is allowed to expose that the email is
   *     already registered
   * @throws ForbiddenOperationException when the current actor is not allowed to assign the
   *     requested roles
   * @throws RoleNotFoundException when an allowed requested role cannot be resolved by the
   *     application
   */
  Result create(Command command);
}
