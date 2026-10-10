/**
 * The API contracts in Spanish, for the API console: each summary and description as
 * docs/api/*.openapi.yaml writes it, and what it says in Spanish. The contracts stay the English
 * source; scripts/check-i18n.mjs fails the build when one of their texts is missing here.
 */
export const ES_API: Record<string, string> = {
  // auth.openapi.yaml
  'Register an institutional account':
    'Registrar una cuenta institucional',
  'Creates a credential record for a member of the university and asks the\nUser API to create the matching profile.\n\nPublic endpoint: no token required.\n\nRules enforced:\n\n- `email` must end in one of the configured domains — `@konradlorenz.edu.co`\n  for institutional accounts, `@kforge.dev` for the team\'s own development\n  ones. Any other domain is rejected with `400`. **There is no guest\n  registration**: a visitor redeems a day pass instead, which creates no\n  account at all.\n- `invitationCode` is mandatory and is what decides the role. A student\n  intake code yields `ROLE_STUDENT`, a staff code yields\n  `ROLE_PROFESSOR`. An unknown, spent or expired code is rejected with\n  `400`. `ROLE_ADMIN` is never granted through this endpoint.\n- The account is created with `emailVerified: false` and a verification\n  e-mail is sent. Login is refused until the address is confirmed.':
    'Crea el registro de credenciales de un miembro de la universidad y le pide a la API de usuarios que cree el perfil correspondiente.\n\nEndpoint público: no requiere token.\n\nReglas que se aplican:\n\n- `email` debe terminar en uno de los dominios configurados: `@konradlorenz.edu.co` para las cuentas institucionales, `@kforge.dev` para las de desarrollo del propio equipo. Cualquier otro dominio se rechaza con `400`. **No existe el registro de invitados**: un visitante canjea un pase de un día, que no crea ninguna cuenta.\n- `invitationCode` es obligatorio y es lo que decide el rol. Un código de ingreso de estudiantes da `ROLE_STUDENT`; un código de personal da `ROLE_PROFESSOR`. Un código desconocido, agotado o vencido se rechaza con `400`. `ROLE_ADMIN` nunca se otorga por este endpoint.\n- La cuenta se crea con `emailVerified: false` y se envía un correo de verificación. El inicio de sesión se rechaza hasta que se confirme la dirección.',
  'Identity of the freshly created account. `emailVerified` is always\n`false` here: the address is only confirmed through `POST /auth/verify`.':
    'Identidad de la cuenta recién creada. Aquí `emailVerified` siempre es `false`: la dirección solo se confirma con `POST /auth/verify`.',
  'Stable identifier shared with the User API. It is the `sub` claim of\nevery token issued for this account.':
    'Identificador estable compartido con la API de usuarios. Es el claim `sub` de todo token emitido para esta cuenta.',
  'Always `false` immediately after registration.':
    'Siempre `false` justo después del registro.',
  'When the service produced this error, in UTC. Not when the request was sent.':
    'Cuándo produjo el servicio este error, en UTC. No cuándo se envió la solicitud.',
  'The HTTP status, repeated in the body so a logged response is self-contained.':
    'El estado HTTP, repetido en el cuerpo para que una respuesta registrada en un log se entienda sola.',
  'The status\'s standard name - `Bad Request`, `Conflict`. For developers, not for users.':
    'El nombre estándar del estado: `Bad Request`, `Conflict`. Para desarrolladores, no para usuarios.',
  'One sentence a person can act on. This is the line worth surfacing in a UI.':
    'Una frase sobre la que una persona puede actuar. Es la línea que vale la pena mostrar en una interfaz.',
  'The path that produced the error, so a log line identifies itself.':
    'La ruta que produjo el error, para que una línea de log se identifique sola.',
  'Field-by-field reasons, when there are any. `field` names what was wrong and `issue` says what about it - this is what a form binds its per-field errors to.':
    'Los motivos campo por campo, cuando los hay. `field` nombra lo que estaba mal e `issue` dice qué tiene: a esto enlaza un formulario sus errores por campo.',
  'Exchange credentials for an access token':
    'Cambiar credenciales por un token de acceso',
  'Verifies the e-mail and password pair and returns a signed RS256 access\ntoken together with the roles the client should use to shape its\nnavigation.\n\nPublic endpoint: no token required.\n\nOutcomes:\n\n- `200` credentials valid and the address is verified.\n- `401` unknown e-mail or wrong password. The message is intentionally\n  identical for both so the response does not reveal which accounts\n  exist.\n- `403` credentials are valid but the address has not been confirmed\n  yet. Clients should route the user to the "resend verification"\n  screen.\n- `403` with an issue for the field `newPassword` in `details`: the\n  password is a temporary one an administrator issued. Clients ask for a\n  new password and send both to `POST /auth/password`.\n- `401` as well for a temporary password past its expiry: an\n  administrator has to issue a new one.\n- `403` with an issue for the field `allowedRoles`: the credentials are\n  valid, and the account has none of the roles the client named. Checked\n  before the temporary password.\n\nThere is no refresh token. When `expiresIn` elapses the client must\nprompt for credentials again.':
    'Verifica el par de correo y contraseña y devuelve un token de acceso firmado con RS256, junto con los roles que el cliente debe usar para armar su navegación.\n\nEndpoint público: no requiere token.\n\nResultados:\n\n- `200` las credenciales son válidas y la dirección está verificada.\n- `401` correo desconocido o contraseña incorrecta. El mensaje es idéntico en ambos casos a propósito, para que la respuesta no revele qué cuentas existen.\n- `403` las credenciales son válidas pero la dirección aún no se ha confirmado. Los clientes deben llevar al usuario a la pantalla de "reenviar verificación".\n- `403` con un problema para el campo `newPassword` en `details`: la contraseña es temporal, la emitió un administrador. Los clientes piden una contraseña nueva y envían las dos a `POST /auth/password`.\n- `401` también para una contraseña temporal vencida: un administrador tiene que emitir otra.\n- `403` con un problema para el campo `allowedRoles`: las credenciales son válidas y la cuenta no tiene ninguno de los roles que nombró el cliente. Se revisa antes que la contraseña temporal.\n\nNo hay token de actualización. Cuando se cumple `expiresIn`, el cliente debe volver a pedir las credenciales.',
  'Replace a password, and sign in with the new one':
    'Cambiar una contraseña e iniciar sesión con la nueva',
  'Sets a new password for an account, given the current one, and returns an\naccess token exactly as `POST /auth/login` does. It is how a temporary\npassword an administrator issued is replaced at first sign-in, and it works\nfor any password.\n\nPublic endpoint: no token required. The current password is the proof, and\nthe gateway rate-limits it like login.\n\nOutcomes:\n\n- `200` the new password is set and the token is for it.\n- `400` the new password is under 10 characters, over 72, or the same as the\n  current one.\n- `401` unknown e-mail, wrong current password, or a temporary password past\n  its expiry. One message for all of them, as with login.\n- `403` the address has not been confirmed yet, or, with an issue for\n  `allowedRoles`, the account has none of the roles the client named. In\n  both cases nothing is changed.':
    'Pone una contraseña nueva a una cuenta, dada la actual, y devuelve un token de acceso igual que `POST /auth/login`. Así se reemplaza en el primer inicio de sesión la contraseña temporal que emitió un administrador, y sirve para cualquier contraseña.\n\nEndpoint público: no requiere token. La contraseña actual es la prueba, y el gateway le limita los intentos como al login.\n\nResultados:\n\n- `200` la contraseña nueva quedó puesta y el token es para ella.\n- `400` la contraseña nueva tiene menos de 10 caracteres, más de 72, o es igual a la actual.\n- `401` correo desconocido, contraseña actual incorrecta o una contraseña temporal vencida. Un solo mensaje para todos, como en el login.\n- `403` la dirección aún no se ha confirmado o, con un problema para `allowedRoles`, la cuenta no tiene ninguno de los roles que nombró el cliente. En ambos casos no se cambia nada.',
  'Create an account with a temporary password':
    'Crear una cuenta con contraseña temporal',
  'Requires `ROLE_ADMIN`. Creates the profile in the User API and the account\nhere, with a temporary password returned once in the response: hand it to\nthe person, who replaces it at first sign-in (`POST /auth/password`). It is\nvalid for seven days.\n\n- `role` is `ROLE_STUDENT` or `ROLE_PROFESSOR`. `ROLE_ADMIN` is never granted\n  this way, as with invitation codes.\n- A student needs `studentCode`. `programCode` may be left out: it is the\n  first three digits of the student code, as the university numbers them.\n- `email` must use one of the configured domains, as in registration.\n- The account may sign in at once: an administrator vouched for it. Its\n  address is still recorded as not verified.':
    'Requiere `ROLE_ADMIN`. Crea el perfil en la API de usuarios y la cuenta aquí, con una contraseña temporal que la respuesta devuelve una sola vez: entréguesela a la persona, que la reemplaza en su primer inicio de sesión (`POST /auth/password`). Sirve siete días.\n\n- `role` es `ROLE_STUDENT` o `ROLE_PROFESSOR`. `ROLE_ADMIN` nunca se otorga así, igual que con los códigos de invitación.\n- Un estudiante necesita `studentCode`. `programCode` puede omitirse: son los tres primeros dígitos del código de estudiante, como los numera la universidad.\n- `email` debe usar uno de los dominios configurados, como en el registro.\n- La cuenta puede iniciar sesión de una vez: un administrador respondió por ella. Su dirección sigue registrada como no verificada.',
  'Delete an account and its profile':
    'Eliminar una cuenta y su perfil',
  'Requires `ROLE_ADMIN`. Removes the account\'s credential, then asks the User\nAPI to remove its profile. Deleting again is safe: an account with no\ncredential left still has its profile removed. Never an administrator\'s\naccount, and never one\'s own: `400`. `503` when the profile could not be\nremoved yet; the account already signs nobody in, and deleting again\nfinishes it. Since 0.3.0.':
    'Requiere `ROLE_ADMIN`. Quita la credencial de la cuenta y luego le pide a la API de usuarios que quite su perfil. Eliminar otra vez es seguro: a una cuenta sin credencial igual se le quita el perfil. Nunca la cuenta de un administrador, ni la propia: `400`. `503` cuando el perfil aún no se pudo quitar; la cuenta ya no deja entrar a nadie, y eliminar otra vez lo termina. Desde 0.3.0.',
  'Delete a profile from another service':
    'Eliminar un perfil desde otro servicio',
  'Service-to-service endpoint. The Auth API calls it when an administrator\ndeletes an account, after removing the account\'s credential. Not reachable\nfrom outside, with `X-Internal-Token` only, as the upsert above.\n\n**Idempotent.** `204` whether or not the profile existed, so a deletion\nthat timed out can be sent again. Since 0.2.0.':
    'Endpoint entre servicios. La API de autenticación lo llama cuando un administrador elimina una cuenta, después de quitar su credencial. No se alcanza desde afuera, solo con `X-Internal-Token`, como el upsert de arriba.\n\n**Idempotente.** `204` exista o no el perfil, así que una eliminación que se quedó sin respuesta se puede enviar otra vez. Desde 0.2.0.',
  'The profile\'s id, the `sub` of the account\'s tokens.':
    'El id del perfil, el `sub` de los tokens de la cuenta.',
  'A temporary password, returned once: it is stored only as a hash. The person\nreplaces it at first sign-in with `POST /auth/password`.':
    'Una contraseña temporal, devuelta una sola vez: solo se guarda su hash. La persona la reemplaza en su primer inicio de sesión con `POST /auth/password`.',
  'The account\'s id, the `sub` of its tokens.':
    'El id de la cuenta, el `sub` de sus tokens.',
  'The account\'s address.':
    'La dirección de la cuenta.',
  'The account\'s role.':
    'El rol de la cuenta.',
  'Three groups of four letters and digits that read out loud without doubt -\nno 0 or O, no 1 or I - joined by hyphens.':
    'Tres grupos de cuatro letras y dígitos que se leen en voz alta sin dudas (sin 0 ni O, sin 1 ni I), unidos con guiones.',
  'When it stops being accepted. Seven days after it was issued.':
    'Cuándo deja de aceptarse. Siete días después de emitida.',
  'Give an account a new temporary password':
    'Darle a una cuenta una contraseña temporal nueva',
  'Requires `ROLE_ADMIN`. For a person who forgot their password: the one they\nhad stops working at once, and the new temporary password, returned once\nhere, must be replaced at their next sign-in. Valid for seven days.':
    'Requiere `ROLE_ADMIN`. Para quien olvidó su contraseña: la que tenía deja de servir de inmediato, y la contraseña temporal nueva, devuelta una sola vez aquí, debe reemplazarse en su próximo inicio de sesión. Sirve siete días.',
  'Signed access token and the minimum context a client needs to render the\nright navigation without an extra profile call. No refresh token is\nissued.':
    'Token de acceso firmado y el mínimo contexto que un cliente necesita para mostrar la navegación correcta sin otra llamada al perfil. No se emite token de actualización.',
  'RS256 JWT. Send it as `Authorization: Bearer <accessToken>`. Validate\nit against `/.well-known/jwks.json` using the `kid` in its header.':
    'JWT RS256. Envíelo como `Authorization: Bearer <accessToken>`. Valídelo contra `/.well-known/jwks.json` usando el `kid` de su encabezado.',
  'Always the literal `Bearer`.':
    'Siempre el literal `Bearer`.',
  'Token lifetime in seconds from issuance. One hour for an account; 24 hours for a\nredeemed visitor pass.':
    'Vida del token en segundos desde su emisión. Una hora para una cuenta; 24 horas para un pase de visitante canjeado.',
  'Whatever the token\'s `sub` claim says. For an account this is its id, a UUID. For\na redeemed visitor pass it is `visitor:<pass id>` — there is no account behind it,\nand a subject that looked like a user id would eventually be treated as one.\n\n**Not declared as `format: uuid`** for exactly that reason: it was, and a client\ngenerated from that would have parsed a visitor\'s subject into a UUID and failed.':
    'Lo que diga el claim `sub` del token. Para una cuenta es su id, un UUID. Para un pase de visitante canjeado es `visitor:<pass id>`: no hay una cuenta detrás, y un sujeto con aspecto de id de usuario terminaría tratándose como uno.\n\n**No se declara como `format: uuid`** justamente por eso: lo estaba, y un cliente generado a partir de ello habría convertido el sujeto de un visitante en un UUID y habría fallado.',
  'Roles granted to the account, mirroring the `roles` claim. Clients\nuse these to hide interface they cannot reach, but authorization is\nalways re-checked server side.':
    'Roles otorgados a la cuenta, reflejo del claim `roles`. Los clientes los usan para ocultar la interfaz a la que no pueden llegar, pero la autorización siempre se vuelve a comprobar en el servidor.',
  'Confirm an e-mail address':
    'Confirmar una dirección de correo',
  'Consumes the single-use token delivered in the verification e-mail and\nmarks the address as confirmed. After a successful call the account can\nlog in.\n\nPublic endpoint: no token required. The `token` in the body is the\nverification token from the e-mail link, not a JWT access token.\n\nThe token is single use: replaying it returns `400`, same as an expired\nor malformed one. Clients should treat `400` as "ask the user to request\na new link" and call `POST /auth/verify/resend`.':
    'Consume el token de un solo uso que llega en el correo de verificación y marca la dirección como confirmada. Después de una llamada exitosa, la cuenta puede iniciar sesión.\n\nEndpoint público: no requiere token. El `token` del cuerpo es el token de verificación del enlace del correo, no un token de acceso JWT.\n\nEl token es de un solo uso: repetirlo devuelve `400`, igual que uno vencido o mal formado. Los clientes deben tratar `400` como "pedirle al usuario que solicite un enlace nuevo" y llamar a `POST /auth/verify/resend`.',
  'Request a new verification e-mail':
    'Solicitar un nuevo correo de verificación',
  'Issues a fresh verification token and sends it to the address supplied.\n\nPublic endpoint: no token required.\n\nThis endpoint **always answers `202`**, whether or not an account with\nthat address exists and whether or not it was already verified. That is\ndeliberate: a per-address distinction would turn this into an account\nenumeration oracle. Clients must show the same neutral confirmation in\nevery case and must never infer account existence from the response.\n\nAny previously issued verification token for the account is invalidated.':
    'Emite un token de verificación nuevo y lo envía a la dirección indicada.\n\nEndpoint público: no requiere token.\n\nEste endpoint **siempre responde `202`**, exista o no una cuenta con esa dirección y esté o no ya verificada. Es deliberado: distinguir por dirección convertiría esto en un oráculo para enumerar cuentas. Los clientes deben mostrar la misma confirmación neutra en todos los casos y nunca deducir de la respuesta si una cuenta existe.\n\nCualquier token de verificación emitido antes para la cuenta queda invalidado.',
  'Neutral acknowledgement. The wording is identical for existing,\nunknown and already-verified addresses.':
    'Acuse neutro. El texto es idéntico para direcciones existentes, desconocidas y ya verificadas.',
  'What was accepted, in a sentence. The work itself may not have finished - this is a 202.':
    'Lo que se aceptó, en una frase. Puede que el trabajo en sí no haya terminado: esto es un 202.',
  'Fetch the JSON Web Key Set':
    'Obtener el conjunto de claves web JSON (JWKS)',
  'Publishes the public half of the RS256 signing keys as a JWKS document.\n\nPublic endpoint: no token required, and it is exposed unauthenticated on\npurpose. Every other KApp service (gateway, user, course, assignment)\nfetches this document, caches it by `kid`, and uses it to validate the\nsignature of incoming access tokens. No service holds the private key\nand no shared secret is distributed.\n\nMore than one key may be present during a rotation: the retiring key\nstays published until the last token signed with it has expired. Consumers\nmust select the key whose `kid` matches the token header rather than\nassuming a single entry.':
    'Publica la mitad pública de las claves de firma RS256 como un documento JWKS.\n\nEndpoint público: no requiere token, y se expone sin autenticación a propósito. Todos los demás servicios de KApp (gateway, user, course, assignment) obtienen este documento, lo guardan en caché por `kid` y lo usan para validar la firma de los tokens de acceso que reciben. Ningún servicio tiene la clave privada y no se reparte ningún secreto compartido.\n\nDurante una rotación puede haber más de una clave: la que se retira sigue publicada hasta que vence el último token firmado con ella. Los consumidores deben elegir la clave cuyo `kid` coincide con el encabezado del token en lugar de suponer que hay una sola.',
  'JSON Web Key Set. Holds every key currently accepted for signature\nvalidation, which is more than one while a rotation is in flight.':
    'Conjunto de claves web JSON. Contiene todas las claves aceptadas en este momento para validar firmas, que son más de una mientras hay una rotación en curso.',
  'Every key currently accepted for signature validation. More than one while a rotation is in flight, which is why this is a set rather than a key.':
    'Todas las claves aceptadas en este momento para validar firmas. Más de una mientras hay una rotación en curso: por eso es un conjunto y no una clave.',
  'RSA public key in JWK form, as defined by RFC 7517.':
    'Clave pública RSA en formato JWK, según la RFC 7517.',
  'Key type. Always `RSA` for KApp signing keys.':
    'Tipo de clave. Siempre `RSA` para las claves de firma de KApp.',
  'Intended use. Always `sig`, these keys never encrypt.':
    'Uso previsto. Siempre `sig`: estas claves nunca cifran.',
  'Key identifier. Matches the `kid` header of tokens signed with this\nkey and is the value consumers must select on.':
    'Identificador de la clave. Coincide con el encabezado `kid` de los tokens firmados con esta clave y es el valor por el que los consumidores deben elegir.',
  'Signing algorithm. Always `RS256`.':
    'Algoritmo de firma. Siempre `RS256`.',
  'RSA modulus, base64url encoded without padding.':
    'Módulo RSA, codificado en base64url sin relleno.',
  'RSA public exponent, base64url encoded. `AQAB` is 65537.':
    'Exponente público RSA, codificado en base64url. `AQAB` es 65537.',
  'Report service liveness':
    'Informar si el servicio está vivo',
  'Service state. `UP` when the process is answering.':
    'Estado del servicio. `UP` cuando el proceso responde.',
  'List invitation codes':
    'Listar los códigos de invitación',
  'Requires `ROLE_ADMIN`. Invitation codes are what gate institutional registration, so\nbeing able to see which exist, how many uses each has left and which are still active\nis the difference between managing access and guessing at it.':
    'Requiere `ROLE_ADMIN`. Los códigos de invitación son los que controlan el registro institucional, así que poder ver cuáles existen, cuántos usos le quedan a cada uno y cuáles siguen activos es la diferencia entre administrar el acceso y adivinarlo.',
  'Filter by whether the code can still be redeemed **right now**, which is narrower than its `active` flag: `true` returns codes that are active, not expired and not spent, and `false` returns every code failing any one of those. Omit it to list all codes. A code with `active: true` that has used all of `maxUses` is therefore returned by `active=false`.':
    'Filtra por si el código se puede canjear **en este momento**, que es más estrecho que su indicador `active`: `true` devuelve los códigos activos, no vencidos y no agotados, y `false` devuelve todos los que fallan en cualquiera de esas condiciones. Omítalo para listar todos los códigos. Por eso, un código con `active: true` que ya usó todos sus `maxUses` aparece con `active=false`.',
  'A code that permits one institutional registration, for a fixed role.':
    'Un código que permite un registro institucional, para un rol fijo.',
  'Generated by the server: `KL-` then two groups of four, from an alphabet with no O/0 or I/1 — the pairs people misread when a code is read out at a counter.':
    'Lo genera el servidor: `KL-` y luego dos grupos de cuatro, de un alfabeto sin O/0 ni I/1, los pares que la gente confunde cuando un código se lee en voz alta en una ventanilla.',
  'The role a registration through this code receives. ROLE_ADMIN is deliberately absent: it is granted by promotion, never by redeeming a code.':
    'El rol que recibe un registro hecho con este código. ROLE_ADMIN no está a propósito: se otorga por promoción, nunca canjeando un código.',
  'How many accounts this code may create in total, across everybody who uses it.':
    'Cuántas cuentas puede crear este código en total, sumando a todos los que lo usan.',
  'Incremented atomically on redemption, so it cannot exceed maxUses.':
    'Se incrementa de forma atómica al canjearlo, así que no puede pasar de maxUses.',
  'Whether it is currently accepted. Deactivating stops redemptions while keeping the record of who already used it.':
    'Si se acepta en este momento. Desactivarlo detiene los canjes y conserva el registro de quién ya lo usó.',
  'Null means it does not expire on its own.':
    'Nulo significa que no vence por sí solo.',
  'Free text for whoever issued it - which intake, who asked. Never shown to the person redeeming it.':
    'Texto libre para quien lo emitió: qué cohorte, quién lo pidió. Nunca se le muestra a quien lo canjea.',
  'Create an invitation code':
    'Crear un código de invitación',
  'Requires `ROLE_ADMIN`. The role a code grants is fixed at creation. `ROLE_ADMIN` is\nnever grantable this way: an administrator must be promoted deliberately, or anyone\nholding a code could escalate.':
    'Requiere `ROLE_ADMIN`. El rol que otorga un código se fija al crearlo. `ROLE_ADMIN` nunca se puede otorgar así: un administrador debe promoverse de forma deliberada, o cualquiera con un código podría escalar privilegios.',
  'Activate or deactivate an invitation code':
    'Activar o desactivar un código de invitación',
  'Requires `ROLE_ADMIN`. Deactivating is the reversible way to stop a code being\nredeemed, and it keeps the record of who used it.':
    'Requiere `ROLE_ADMIN`. Desactivar es la forma reversible de impedir que un código se canjee, y conserva el registro de quién lo usó.',
  'Delete an invitation code':
    'Eliminar un código de invitación',
  'Requires `ROLE_ADMIN`. Prefer deactivating: deleting a code that has already been\nredeemed discards the only record of how those accounts were created.':
    'Requiere `ROLE_ADMIN`. Es mejor desactivarlo: eliminar un código que ya se canjeó descarta el único registro de cómo se crearon esas cuentas.',
  'Redeem a visitor day pass':
    'Canjear un pase de visitante de un día',
  'Exchanges a pass for a token valid for **24 hours** that opens the campus map and\nnothing else.\n\nPublic, necessarily: a visitor has no account and nothing to authenticate with. The\ncode *is* the credential, which is why it is single-use, stops being redeemable 12\nhours after it was issued, and is rate-limited at the gateway alongside `/auth/login`.\n\nThe returned token carries `ROLE_GUEST` and no other role. That is not a special\nvisitor permission — it is the role every other service already refuses everywhere\nbut the map, so "map only" is the authorization matrix the services enforce rather\nthan a second mechanism that could drift away from it. The `sub` claim is\n`visitor:<pass id>`, not a user id, and there is no `email` claim: there is no\naccount behind it.\n\n**The identity document is required.** The pass exists so reception has a record of\nwho was in the building; redeemed anonymously it would be an account with extra\nsteps. That record is personal data under Ley 1581 de 2012 and is **deleted\nautomatically 30 days** after redemption.':
    'Cambia un pase por un token válido por **24 horas** que abre el mapa del campus y nada más.\n\nEs público por necesidad: un visitante no tiene cuenta ni nada con qué autenticarse. El código *es* la credencial; por eso es de un solo uso, deja de poder canjearse 12 horas después de emitido y tiene límite de solicitudes en el gateway junto con `/auth/login`.\n\nEl token devuelto lleva `ROLE_GUEST` y ningún otro rol. No es un permiso especial para visitantes: es el rol que todos los demás servicios ya rechazan en todas partes salvo en el mapa, así que "solo el mapa" es la matriz de autorización que los servicios aplican y no un segundo mecanismo que podría desviarse de ella. El claim `sub` es `visitor:<pass id>`, no un id de usuario, y no hay claim `email`: no hay una cuenta detrás.\n\n**El documento de identidad es obligatorio.** El pase existe para que recepción tenga un registro de quién estuvo en el edificio; canjeado de forma anónima sería una cuenta con pasos de más. Ese registro es un dato personal según la Ley 1581 de 2012 y se **elimina automáticamente a los 30 días** del canje.',
  'The code reception read out to the visitor.':
    'El código que recepción le leyó al visitante.',
  'Read the visitor register':
    'Consultar el registro de visitantes',
  'Requires `ROLE_ADMIN`. Newest first.\n\n**This returns personal data**: visitors\' names and identity documents. That is the\npoint of a register, and the reason it exists nowhere else in the API. Records are\ndeleted automatically 30 days after redemption.':
    'Requiere `ROLE_ADMIN`. Primero los más recientes.\n\n**Esto devuelve datos personales**: los nombres y documentos de identidad de los visitantes. Ese es el objetivo de un registro, y la razón por la que no existe en ninguna otra parte de la API. Los registros se eliminan automáticamente 30 días después del canje.',
  'Filter to passes that have, or have not, been used.':
    'Filtra los pases que se han usado, o los que no.',
  'A pass as reception sees it, including who redeemed it. Returned only to\n`ROLE_ADMIN`, and only from `/auth/admin/**`.':
    'Un pase como lo ve recepción, incluido quién lo canjeó. Solo se devuelve a `ROLE_ADMIN`, y solo desde `/auth/admin/**`.',
  'What reception reads out to the visitor. Grouped in fours because it is read aloud.':
    'Lo que recepción le lee al visitante. Va en grupos de cuatro porque se lee en voz alta.',
  'The administrator who minted it, so a pass traces back to a person.':
    'El administrador que lo emitió, para que un pase lleve hasta una persona.',
  'Free text for reception - who the visitor came to see, which event.':
    'Texto libre para recepción: a quién vino a ver el visitante, qué evento.',
  'When reception issued the pass - not when it was used.':
    'Cuándo emitió recepción el pase, no cuándo se usó.',
  'After this the code is dead whether or not it was used. A pass is for today.':
    'Después de esto el código queda muerto, se haya usado o no. Un pase es para hoy.',
  'Once true this row is the record of a visit, not an unused pass.':
    'Una vez en true, esta fila es el registro de una visita, no un pase sin usar.',
  'When the visitor presented it and received their token. Null while the pass is still unused.':
    'Cuándo lo presentó el visitante y recibió su token. Nulo mientras el pase siga sin usar.',
  'The kinds of identity document a visitor may present, as used in Colombia. Stored\nrather than inferred from the number\'s shape: a `TI` and a `CC` can carry the same\ndigits for the same person at different ages, and telling reception which document\nthey were shown is the point of keeping the record.':
    'Los tipos de documento de identidad que puede presentar un visitante, como se usan en Colombia. Se guarda en lugar de deducirse de la forma del número: una `TI` y una `CC` pueden llevar los mismos dígitos para la misma persona a distintas edades, y decirle a recepción qué documento le mostraron es el objetivo de guardar el registro.',
  'Present only once redeemed. Personal data; deleted after 30 days.':
    'Solo aparece una vez canjeado. Dato personal; se elimina a los 30 días.',
  'Taken from the identity document at the counter, for the register.':
    'Tomado del documento de identidad en la ventanilla, para el registro.',
  'When the issued token stops working, 24 hours after redemption.':
    'Cuándo deja de funcionar el token emitido, 24 horas después del canje.',
  'When this whole record disappears. Enforced by a MongoDB TTL index rather than by\na scheduled job — a job that stops running leaves personal data sitting there,\nand nobody notices until somebody asks.':
    'Cuándo desaparece este registro completo. Lo hace cumplir un índice TTL de MongoDB y no una tarea programada: una tarea que deja de ejecutarse deja datos personales ahí, y nadie se entera hasta que alguien pregunta.',
  'Issue a visitor day pass':
    'Emitir un pase de visitante de un día',
  'Requires `ROLE_ADMIN`. Returns a code to read out to the visitor.\n\nThe code avoids `I`, `O`, `0` and `1`: it is read aloud across a counter and typed by\nsomebody who has never seen it written down.\n\nRedeemable for 12 hours. Once redeemed the visitor has 24 hours of map-only access.':
    'Requiere `ROLE_ADMIN`. Devuelve un código para leerle al visitante.\n\nEl código evita `I`, `O`, `0` y `1`: se lee en voz alta en una ventanilla y lo escribe alguien que nunca lo ha visto escrito.\n\nSe puede canjear durante 12 horas. Una vez canjeado, el visitante tiene 24 horas de acceso solo al mapa.',
  'Revoke an unredeemed pass':
    'Revocar un pase sin canjear',
  'Requires `ROLE_ADMIN`.\n\n`409` for a pass that has already been redeemed. That row is no longer a pass, it is\nthe record of a visit, and a register reception could quietly edit is not a register.\nIt leaves on its own after 30 days.':
    'Requiere `ROLE_ADMIN`.\n\n`409` para un pase que ya se canjeó. Esa fila ya no es un pase, es el registro de una visita, y un registro que recepción pudiera editar en silencio no es un registro. Se va sola a los 30 días.',
  'Allow or refuse sign-in for an account':
    'Permitir o negar el inicio de sesión de una cuenta',
  'Service-to-service endpoint. The User API calls it when an administrator\ndeactivates or reactivates an account, because whether somebody may sign\nin is decided here while the profile flag lives there.\n\nBefore this endpoint existed, "deactivate" wrote `active = false` on the\nprofile and nothing else. Sign-in only ever refuses a `SUSPENDED`\ncredential, and nothing in the product ever set `SUSPENDED` — so the\nbutton claimed to remove access and the account kept working.\n\n**Not reachable from outside.** The API gateway does not route\n`/internal/**`; the path exists only on the auth-service port inside the\nDocker network. Do not build any client against it — no web, Kotlin or\nSwift client should ever call it.\n\n**Access: `X-Internal-Token` header only.** No bearer token is involved\nand no user role applies. A missing or wrong token gives `401`.\n\n**A token already issued stays valid until it expires.** This stops the\nnext one being issued; it does not revoke one in flight. That is\ninherent to stateless JWTs and it is why the access-token lifetime is an\nhour.\n\nIdempotent: setting the state the credential already holds answers `204`\nand writes nothing. Restoring an account lands on `ACTIVE`, including one\nthat was suspended while its e-mail was unverified — verification is off\nin the MVP, so every credential carries `emailVerified: false` and\ntreating that as "send it back to PENDING_VERIFICATION" would make\ndeactivation a one-way door.':
    'Endpoint entre servicios. La API de usuarios lo llama cuando un administrador desactiva o reactiva una cuenta, porque si alguien puede iniciar sesión se decide aquí, mientras que el indicador del perfil vive allá.\n\nAntes de que existiera este endpoint, "desactivar" escribía `active = false` en el perfil y nada más. El inicio de sesión solo rechaza una credencial `SUSPENDED`, y nada en el producto ponía nunca `SUSPENDED`: el botón decía quitar el acceso y la cuenta seguía funcionando.\n\n**No se puede llegar desde afuera.** El gateway de la API no enruta `/internal/**`; la ruta solo existe en el puerto del auth-service dentro de la red de Docker. No construya ningún cliente contra ella: ningún cliente web, Kotlin ni Swift debe llamarla.\n\n**Acceso: solo con el encabezado `X-Internal-Token`.** No interviene ningún token bearer y no aplica ningún rol de usuario. Un token ausente o incorrecto da `401`.\n\n**Un token ya emitido sigue siendo válido hasta que vence.** Esto impide que se emita el siguiente; no revoca uno en uso. Es inherente a los JWT sin estado, y por eso la vida del token de acceso es de una hora.\n\nIdempotente: fijar el estado que la credencial ya tiene responde `204` y no escribe nada. Restaurar una cuenta la deja en `ACTIVE`, incluida una que se suspendió mientras su correo no estaba verificado: la verificación está apagada en el MVP, así que toda credencial lleva `emailVerified: false`, y tratar eso como "devolverla a PENDING_VERIFICATION" haría de la desactivación una puerta de un solo sentido.',
  'Account identifier, the same one the User API keys its profile on and the `sub` claim of every token signed for this account.':
    'Identificador de la cuenta, el mismo por el que la API de usuarios indexa su perfil y el claim `sub` de todo token firmado para esta cuenta.',
  // user.openapi.yaml
  'Get the authenticated user\'s profile':
    'Obtener el perfil del usuario autenticado',
  'Returns the profile of the account the bearer token belongs to,\nresolved from the token\'s `sub` claim. No user id is accepted in the\nrequest, so one account can never read another through this path.\n\n**Access: every authenticated role except `ROLE_GUEST`** — `ROLE_STUDENT`,\n`ROLE_PROFESSOR` and `ROLE_ADMIN` reach their own profile here.\n\n`ROLE_GUEST` is refused with `403`. It used to be allowed, when a guest was an account\nwhose `academic` block was null. A guest is now a visitor holding a day pass: there is\nno account, the token\'s subject is `visitor:<pass id>`, and this endpoint could only\never have answered `404`.':
    'Devuelve el perfil de la cuenta a la que pertenece el token bearer, resuelto a partir del claim `sub` del token. La solicitud no acepta ningún id de usuario, así que una cuenta nunca puede leer otra por esta ruta.\n\n**Acceso: todos los roles autenticados salvo `ROLE_GUEST`**: `ROLE_STUDENT`, `ROLE_PROFESSOR` y `ROLE_ADMIN` llegan aquí a su propio perfil.\n\n`ROLE_GUEST` se rechaza con `403`. Antes se permitía, cuando un invitado era una cuenta cuyo bloque `academic` era nulo. Ahora un invitado es un visitante con un pase de un día: no hay cuenta, el sujeto del token es `visitor:<pass id>` y este endpoint solo habría podido responder `404`.',
  'A KApp user profile. Contains no credential data.\n\nTwo fields are nullable by design. `identification` is null until the\nuser completes their profile. `academic` is null for every guest and\npopulated for every member of the university, so clients must branch on\nit rather than assume it is present.':
    'Un perfil de usuario de KApp. No contiene datos de credenciales.\n\nDos campos admiten nulo por diseño. `identification` es nulo hasta que el usuario completa su perfil. `academic` es nulo para todo invitado y está lleno para todo miembro de la universidad, así que los clientes deben decidir según él en lugar de suponer que está presente.',
  'Account identifier, shared with the Auth API. It is the `sub` claim\nof every token issued for this account.':
    'Identificador de la cuenta, compartido con la API de autenticación. Es el claim `sub` de todo token emitido para esta cuenta.',
  'Address the account authenticates with. Institutional accounts use\n`@konradlorenz.edu.co`, guests may use any domain. Read-only here;\nit is owned by the Auth API.':
    'Dirección con la que se autentica la cuenta. Las cuentas institucionales usan `@konradlorenz.edu.co`; los invitados pueden usar cualquier dominio. Aquí es de solo lectura; le pertenece a la API de autenticación.',
  'Identity document, or null while the user has not supplied one.':
    'Documento de identidad, o nulo mientras el usuario no lo haya dado.',
  'Colombian identity document. Null until the user fills it in.':
    'Documento de identidad colombiano. Nulo hasta que el usuario lo llena.',
  'Document class: `CC` cédula de ciudadanía, `TI` tarjeta de\nidentidad, `CE` cédula de extranjería, `PASAPORTE` passport.':
    'Clase de documento: `CC` cédula de ciudadanía, `TI` tarjeta de identidad, `CE` cédula de extranjería, `PASAPORTE` pasaporte.',
  'Document number as printed, kept as a string so leading zeros and\npassport letters survive.':
    'Número del documento tal como está impreso, guardado como texto para que sobrevivan los ceros a la izquierda y las letras de un pasaporte.',
  'Contact number in E.164 form, or null.':
    'Número de contacto en formato E.164, o nulo.',
  'Profile picture URL, or null when the user has not uploaded one.':
    'URL de la foto de perfil, o nulo cuando el usuario no ha subido una.',
  'Single role held by the account. Read-only here; it is assigned at\nregistration and changed only by the registrar.':
    'El único rol que tiene la cuenta. Aquí es de solo lectura; se asigna en el registro y solo lo cambia registro académico.',
  'False after an administrative deactivation. A deactivated account\nkeeps its profile for audit but cannot use the platform.':
    'Falso después de una desactivación administrativa. Una cuenta desactivada conserva su perfil para auditoría pero no puede usar la plataforma.',
  'Academic record, or **null for guests**. Always null when `role` is\n`ROLE_GUEST`.':
    'Registro académico, o **nulo para los invitados**. Siempre nulo cuando `role` es `ROLE_GUEST`.',
  'Academic record of a member of the university. **Null for every\n`ROLE_GUEST` account**: a guest has no student code, no program and no\npensum.':
    'Registro académico de un miembro de la universidad. **Nulo para toda cuenta `ROLE_GUEST`**: un invitado no tiene código de estudiante, ni programa, ni pensum.',
  'University student code.':
    'Código de estudiante de la universidad.',
  'Academic program code. `506` is Ingeniería de Sistemas.':
    'Código del programa académico. `506` es Ingeniería de Sistemas.',
  'Pensum version the student is bound to. Students who enrolled\nunder an earlier pensum keep their original pensum, so this is\nnot derivable from the program alone.':
    'Versión del pensum a la que está ligado el estudiante. Quienes se matricularon con un pensum anterior conservan el suyo, así que no se puede deducir solo del programa.',
  'Semester the student is currently enrolled in.':
    'Semestre en el que está matriculado el estudiante.',
  'Update the authenticated user\'s profile':
    'Actualizar el perfil del usuario autenticado',
  'Partially updates the caller\'s own profile. Only the fields present in\nthe body are touched; omitting a field leaves it unchanged.\n\n**Access: any authenticated role.** The target is always the token\'s\n`sub`, so no one can edit another account here.\n\nEditable: `firstName`, `lastName`, `phone`, `avatarUrl`,\n`identification`, `academic`.\n\n**Not editable here.** `email` is owned by the Auth API and changing it\nwould break the credential link. `role` and `active` are administrative\nand are only changed through the endpoints under `/api/users`. Sending\n`email`, `role`, `active` or `id` is rejected with `400` rather than\nbeing silently dropped, so a client that round-trips a full profile\nobject gets a clear error instead of a surprise.\n\nRejected with `400`: an empty body, an `academic` object on a\n`ROLE_GUEST` account, and any field that fails its own constraints.\n\n**Access: every authenticated role except `ROLE_GUEST`**, for the reason above.\n`identification` and `academic` may be set to `null` to clear them.':
    'Actualiza parcialmente el perfil propio de quien llama. Solo se tocan los campos presentes en el cuerpo; omitir un campo lo deja igual.\n\n**Acceso: cualquier rol autenticado.** El destino siempre es el `sub` del token, así que nadie puede editar otra cuenta aquí.\n\nEditables: `firstName`, `lastName`, `phone`, `avatarUrl`, `identification`, `academic`.\n\n**No se editan aquí.** `email` le pertenece a la API de autenticación y cambiarlo rompería el vínculo con la credencial. `role` y `active` son administrativos y solo se cambian con los endpoints de `/api/users`. Enviar `email`, `role`, `active` o `id` se rechaza con `400` en lugar de descartarse en silencio, así que un cliente que reenvía el perfil completo recibe un error claro en vez de una sorpresa.\n\nSe rechaza con `400`: un cuerpo vacío, un objeto `academic` en una cuenta `ROLE_GUEST` y cualquier campo que no cumpla sus propias restricciones.\n\n**Acceso: todos los roles autenticados salvo `ROLE_GUEST`**, por la razón de arriba. `identification` y `academic` se pueden poner en `null` para borrarlos.',
  'List and search user profiles':
    'Listar y buscar perfiles de usuario',
  'Returns a page of profiles from the directory, newest account first.\n\n**Access: `ROLE_ADMIN` only.** Any other authenticated role receives\n`403`. This is the only endpoint that exposes accounts other than the\ncaller\'s own, which is why it is restricted.\n\nFilters combine with AND: `role`, `active` and `q` narrow the same\nresult set. Omitting all three lists every account.':
    'Devuelve una página de perfiles del directorio, primero las cuentas más recientes.\n\n**Acceso: solo `ROLE_ADMIN`.** Cualquier otro rol autenticado recibe `403`. Es el único endpoint que expone cuentas distintas a la de quien llama, y por eso está restringido.\n\nLos filtros se combinan con Y: `role`, `active` y `q` acotan el mismo conjunto de resultados. Sin ninguno de los tres se listan todas las cuentas.',
  'Zero-based page index.':
    'Índice de página, empezando en cero.',
  'Profiles per page. Values above 100 are rejected with `400`.':
    'Perfiles por página. Los valores mayores a 100 se rechazan con `400`.',
  'Return only accounts holding this role.':
    'Devolver solo las cuentas con este rol.',
  'Return only active (`true`) or only deactivated (`false`) accounts.\nOmit to return both.':
    'Devolver solo las cuentas activas (`true`) o solo las desactivadas (`false`). Omítalo para devolver ambas.',
  'Free-text search, case and accent insensitive, matched as a partial\nagainst first name, last name and e-mail.':
    'Búsqueda de texto libre, sin distinguir mayúsculas ni tildes, que coincide de forma parcial con el nombre, el apellido y el correo.',
  'One page of user profiles. Page indexes are zero-based, so `page: 0` is\nthe first page and `first` is true there.':
    'Una página de perfiles de usuario. Los índices de página empiezan en cero, así que `page: 0` es la primera página y ahí `first` es verdadero.',
  'Profiles in this page. Empty when the filters match nothing.':
    'Perfiles de esta página. Vacío cuando los filtros no coinciden con nada.',
  'Zero-based index of this page.':
    'Índice de esta página, empezando en cero.',
  'Page size requested. The last page may hold fewer items.':
    'Tamaño de página pedido. La última página puede tener menos elementos.',
  'Profiles matching the filters across every page.':
    'Perfiles que coinciden con los filtros en todas las páginas.',
  'Number of pages available. Zero when nothing matches.':
    'Número de páginas disponibles. Cero cuando nada coincide.',
  'True when `page` is 0.':
    'Verdadero cuando `page` es 0.',
  'True when this is the final page.':
    'Verdadero cuando esta es la última página.',
  'Get a user profile by id':
    'Obtener un perfil de usuario por id',
  'Returns a single profile by account id.\n\n**Access: `ROLE_ADMIN` only.** Other roles receive `403` even when the\nid is their own; they read their profile through `GET /api/users/me`.\n\nReturns `404` when no account carries that id. A well-formed but unknown\nUUID is a `404`, not a `400`.':
    'Devuelve un solo perfil por id de cuenta.\n\n**Acceso: solo `ROLE_ADMIN`.** Los demás roles reciben `403` aunque el id sea el suyo; su perfil lo leen con `GET /api/users/me`.\n\nDevuelve `404` cuando ninguna cuenta tiene ese id. Un UUID bien formado pero desconocido es un `404`, no un `400`.',
  'Account identifier, shared with the Auth API as the token `sub` claim.':
    'Identificador de la cuenta, compartido con la API de autenticación como el claim `sub` del token.',
  'Activate or deactivate a user':
    'Activar o desactivar un usuario',
  'Flips the activation flag of an account. Deactivation is a logical\ndelete: the profile is kept for audit and academic history, and the\naccount can no longer sign in.\n\n**This writes to two services.** The profile here stops being listed as\nactive, and the User API calls\n`PATCH /internal/credentials/{userId}/status` on the Auth API to suspend\nthe credential — because whether somebody may sign in is decided there,\nnot here. The credential is changed first, so a failure part-way leaves\nthe account locked out rather than listed as inactive while its owner\ncan still log in. If the Auth API cannot be reached, this call fails and\nnothing changes.\n\n**A token already issued stays valid until it expires.** Nothing revokes\none; deactivation stops the next one being issued. That is inherent to\nstateless JWTs and it is why the access-token lifetime is an hour.\n\n**Access: `ROLE_ADMIN` only.** Other roles receive `403`.\n\nThe call is idempotent: setting `active` to the value it already holds\nsucceeds and returns the unchanged profile, writing nothing on either\nside. Returns `404` when no account carries that id.':
    'Cambia el indicador de activación de una cuenta. Desactivar es un borrado lógico: el perfil se conserva para auditoría e historia académica, y la cuenta ya no puede iniciar sesión.\n\n**Esto escribe en dos servicios.** El perfil de aquí deja de aparecer como activo, y la API de usuarios llama a `PATCH /internal/credentials/{userId}/status` en la API de autenticación para suspender la credencial, porque si alguien puede iniciar sesión se decide allá, no aquí. La credencial se cambia primero, así que una falla a mitad de camino deja la cuenta bloqueada en lugar de marcada como inactiva mientras su dueño todavía puede entrar. Si no se puede llegar a la API de autenticación, esta llamada falla y nada cambia.\n\n**Un token ya emitido sigue siendo válido hasta que vence.** Nada lo revoca; desactivar impide que se emita el siguiente. Es inherente a los JWT sin estado, y por eso la vida del token de acceso es de una hora.\n\n**Acceso: solo `ROLE_ADMIN`.** Los demás roles reciben `403`.\n\nLa llamada es idempotente: fijar `active` en el valor que ya tiene funciona y devuelve el perfil sin cambios, sin escribir nada en ninguno de los dos lados. Devuelve `404` cuando ninguna cuenta tiene ese id.',
  'Create or update a profile from another service':
    'Crear o actualizar un perfil desde otro servicio',
  'Service-to-service endpoint. The Auth API calls it during registration\nto materialise the profile that matches a freshly created credential\nrecord.\n\n**Not reachable from outside.** The API gateway does not route\n`/internal/**`; the path exists only on the user-service port inside the\nDocker network. A request arriving at the gateway for this path gets a\n`404` from the gateway itself and never reaches this service. Do not\nbuild any client against it — no web, Kotlin or Swift client should ever\ncall it.\n\n**Access: `X-Internal-Token` header only.** No bearer token is involved\nand no user role applies. The token is a shared secret injected as an\nenvironment variable in both services. A missing or wrong token gives\n`401`.\n\n**Idempotent upsert keyed by e-mail.** A first call creates the profile;\na repeated call with the same e-mail updates the existing one and\nreturns it. Both return `200`, never `201`, and there is no `409`, so a\nretry after a network timeout is always safe and the registration flow\ncan be replayed without creating duplicates.\n\nThe `id` in the response is the profile identifier the Auth API stores\nand puts in the `sub` claim of every token it signs for this account.\n\n`academic` must be `null` for `ROLE_GUEST` and must be present for\n`ROLE_STUDENT`; a mismatch is rejected with `400`.':
    'Endpoint entre servicios. La API de autenticación lo llama durante el registro para crear el perfil que corresponde a un registro de credenciales recién creado.\n\n**No se puede llegar desde afuera.** El gateway de la API no enruta `/internal/**`; la ruta solo existe en el puerto del user-service dentro de la red de Docker. Una solicitud que llega al gateway por esta ruta recibe un `404` del propio gateway y nunca llega a este servicio. No construya ningún cliente contra ella: ningún cliente web, Kotlin ni Swift debe llamarla.\n\n**Acceso: solo con el encabezado `X-Internal-Token`.** No interviene ningún token bearer y no aplica ningún rol de usuario. El token es un secreto compartido que se inyecta como variable de entorno en ambos servicios. Un token ausente o incorrecto da `401`.\n\n**Upsert idempotente por correo.** Una primera llamada crea el perfil; otra con el mismo correo actualiza el existente y lo devuelve. Ambas responden `200`, nunca `201`, y no hay `409`, así que reintentar después de un timeout de red siempre es seguro y el flujo de registro se puede repetir sin crear duplicados.\n\nEl `id` de la respuesta es el identificador de perfil que guarda la API de autenticación y que pone en el claim `sub` de todo token que firma para esta cuenta.\n\n`academic` debe ser `null` para `ROLE_GUEST` y debe estar presente para `ROLE_STUDENT`; si no coincide, se rechaza con `400`.',
  // semaphore.openapi.yaml
  'List academic programs':
    'Listar los programas académicos',
  'Returns every academic program in the catalog, each with the code of its currently\n`ACTIVE` pensum. `activePensumCode` is `null` for a program whose only pensums are\n`DRAFT` or `OBSOLETE`.':
    'Devuelve todos los programas académicos del catálogo, cada uno con el código de su pensum `ACTIVE` actual. `activePensumCode` es `null` para un programa cuyos únicos pensums son `DRAFT` u `OBSOLETE`.',
  'An academic program offered by the university.':
    'Un programa académico que ofrece la universidad.',
  'Institutional program code.':
    'Código institucional del programa.',
  'Name of the faculty that owns the program.':
    'Nombre de la facultad a la que pertenece el programa.',
  'Academic level of the program, matching the KApp `program_level_enum`.':
    'Nivel académico del programa, igual al `program_level_enum` de KApp.',
  'Code of the program\'s currently `ACTIVE` pensum, or `null` if it has none.\nThis is the pensum that `GET /api/semaphore/me` pins for new students.':
    'Código del pensum `ACTIVE` actual del programa, o `null` si no tiene. Es el pensum que `GET /api/semaphore/me` fija para los estudiantes nuevos.',
  'Create an academic program':
    'Crear un programa académico',
  'Requires `ROLE_ADMIN`. The institutional code is supplied by the caller rather than\ngenerated: it already identifies the program in the university\'s own records.':
    'Requiere `ROLE_ADMIN`. El código institucional lo da quien llama en lugar de generarse: ya identifica al programa en los registros propios de la universidad.',
  'Get one academic program':
    'Obtener un programa académico',
  'Returns a single program by its institutional code.':
    'Devuelve un solo programa por su código institucional.',
  'Replace an academic program':
    'Reemplazar un programa académico',
  'Requires `ROLE_ADMIN`. The code in the path wins; a differing code in the body is a 400.':
    'Requiere `ROLE_ADMIN`. Vale el código de la ruta; un código distinto en el cuerpo es un 400.',
  'Delete an academic program':
    'Eliminar un programa académico',
  'Requires `ROLE_ADMIN`. Refused with `409` while any pensum still references it:\nremoving the program would orphan every student following those pensums, and a\nsilent cascade is worse than an error a human can read.':
    'Requiere `ROLE_ADMIN`. Se rechaza con `409` mientras algún pensum lo siga referenciando: quitar el programa dejaría huérfanos a todos los estudiantes que siguen esos pensums, y un borrado en cascada silencioso es peor que un error que una persona puede leer.',
  'Get a full pensum':
    'Obtener un pensum completo',
  'Returns the whole pensum document: header, knowledge areas, and **every** pensum item\n(roughly 51 for the Ingeniería de Sistemas Reforma 2018 plan), including the six elective\nslots. This is the single call a client needs to render the semáforo grid.\n\nThe example below is a representative slice of the real plan — it spans levels 1 through 9\nand all four areas — and is not the complete 51-item list.':
    'Devuelve el documento completo del pensum: encabezado, áreas de conocimiento y **todos** los elementos del pensum (unos 51 en el plan de Ingeniería de Sistemas Reforma 2018), incluidos los seis espacios de electiva. Es la única llamada que un cliente necesita para dibujar la cuadrícula del semáforo.\n\nEl ejemplo de abajo es una muestra representativa del plan real (abarca los niveles 1 a 9 y las cuatro áreas) y no la lista completa de 51 elementos.',
  'Institutional pensum (pensum) code.':
    'Código institucional del pensum.',
  'A pensum — the whole degree plan a semáforo is drawn from.':
    'Un pensum: el plan de estudios completo del que se dibuja un semáforo.',
  'Institutional code of the plan, unique across the catalog.':
    'Código institucional del plan, único en todo el catálogo.',
  'Name of the curricular reform this plan belongs to.':
    'Nombre de la reforma curricular a la que pertenece este plan.',
  'Lifecycle state of the plan. Only an `ACTIVE` plan is pinned to new students by\n`GET /api/semaphore/me`.':
    'Estado del ciclo de vida del plan. `GET /api/semaphore/me` solo fija un plan `ACTIVE` para los estudiantes nuevos.',
  'Sum of the credits of every item in the plan.':
    'Suma de los créditos de todos los elementos del plan.',
  'The **weekly** hours the printed plan declares — not semester hours, and not\nrecomputed from the items. Where the document prints no total, it is the sum of the\nitems and carries a half wherever one of them does.':
    'Las horas **semanales** que declara el plan impreso: no son horas del semestre y no se recalculan a partir de los elementos. Donde el documento no imprime un total, es la suma de los elementos y lleva media hora siempre que uno de ellos la lleve.',
  'Number of levels (semesters) the plan spans — the number of grid columns.':
    'Número de niveles (semestres) que abarca el plan: el número de columnas de la cuadrícula.',
  'The knowledge areas of the plan — the grid rows, in display order.':
    'Las áreas de conocimiento del plan: las filas de la cuadrícula, en el orden en que se muestran.',
  'A knowledge area — one row of the semáforo grid. Area codes are defined per pensum;\nthe Ingeniería de Sistemas Reforma 2018 plan uses `CB`, `BIS`, `ISA` and `SI`.':
    'Un área de conocimiento: una fila de la cuadrícula del semáforo. Los códigos de área se definen por pensum; el plan de Ingeniería de Sistemas Reforma 2018 usa `CB`, `BIS`, `ISA` y `SI`.',
  'Short area code, unique within the pensum. Twenty characters, because a printed pensum\'s area column holds words (COMPLEMENTARIA), not slugs.':
    'Código corto del área, único dentro del pensum. Veinte caracteres, porque la columna de área de un pensum impreso lleva palabras (COMPLEMENTARIA), no identificadores.',
  'Brand colour of the area as an RGB hex triplet, used to tint its row.':
    'Color de marca del área como un triplete RGB hexadecimal, que se usa para teñir su fila.',
  'Total credits the plan assigns to this area.':
    'Total de créditos que el plan asigna a esta área.',
  'Total **weekly** hours the plan assigns to this area. A sum of its items, so it\ncarries a half wherever one of them does.':
    'Total de horas **semanales** que el plan asigna a esta área. Es la suma de sus elementos, así que lleva media hora siempre que uno de ellos la lleve.',
  'Every item of the plan, fixed courses and elective slots alike. Roughly 51 items for\nthe Ingeniería de Sistemas Reforma 2018 plan.':
    'Todos los elementos del plan, tanto materias fijas como espacios de electiva. Unos 51 elementos en el plan de Ingeniería de Sistemas Reforma 2018.',
  'One item of a pensum — a fixed course, or an elective slot with no fixed content.\n\nFor an elective slot, `code` is `null` and `isElectiveSlot` is `true`; the slot is\naddressed by `pensumItemCode`. For a fixed course, `code` is always present.\n\n**`code` is an identifier, `sinuCode` is what you display.** See "Not every `code` is a\nreal course code" above.':
    'Un elemento de un pensum: una materia fija, o un espacio de electiva sin contenido fijo.\n\nPara un espacio de electiva, `code` es `null` e `isElectiveSlot` es `true`; el espacio se identifica por `pensumItemCode`. Para una materia fija, `code` siempre está presente.\n\n**`code` es un identificador; `sinuCode` es lo que se muestra.** Vea "No todo `code` es un código de materia real" más arriba.',
  'The code this item is addressed by. `null` for an elective slot, which has no fixed\ncontent.\n\nIt is the institutional course code where the plan prints one, and a KApp-generated\nplaceholder where it does not — `sinuCode` says which. **Not for display**: put\n`sinuCode` on screen, or nothing.':
    'El código por el que se identifica este elemento. `null` para un espacio de electiva, que no tiene contenido fijo.\n\nEs el código institucional de la materia donde el plan imprime uno, y un marcador generado por KApp donde no; `sinuCode` dice cuál es. **No es para mostrar**: ponga `sinuCode` en pantalla, o nada.',
  'Stable identifier of this item **within the pensum**. Unlike `code`, it is never\n`null`, so it is what elective slots are addressed by (for example `"ELECTIVA_VI"`).':
    'Identificador estable de este elemento **dentro del pensum**. A diferencia de `code`, nunca es `null`, así que es por lo que se identifican los espacios de electiva (por ejemplo `"ELECTIVA_VI"`).',
  'The level (semester) this item sits in — the grid column.':
    'El nivel (semestre) en el que está este elemento: la columna de la cuadrícula.',
  'Academic credits awarded by the item.':
    'Créditos académicos que otorga el elemento.',
  'Contact hours per week: a whole number of hours, or a whole number and a half.\n\nOnly halves; `4.3` is rejected. A whole figure is written whole (`4`, not `4.0`).':
    'Horas de contacto por semana: un número entero de horas, o un entero y media.\n\nSolo medias; `4.3` se rechaza. Una cifra entera se escribe entera (`4`, no `4.0`).',
  'Contact hours across the full 16-week semester. Derived: `weeklyHours * 16`.\n\nOmit it on write and the server derives it; supply it and it must satisfy the\ninvariant or the request is rejected with `400`. It is always present on read, which\nis why it is not in `required`.':
    'Horas de contacto en todo el semestre de 16 semanas. Se calcula: `weeklyHours * 16`.\n\nOmítalo al escribir y el servidor lo calcula; si lo envía, debe cumplir la regla o la solicitud se rechaza con `400`. Siempre está presente al leer, y por eso no está en `required`.',
  'Code of the knowledge area this item belongs to — the grid row.':
    'Código del área de conocimiento a la que pertenece este elemento: la fila de la cuadrícula.',
  'The course code as the university\'s own system (SINU) carries it, or `null` where it\nhas not been confirmed.\n\nThis is the only field that says a code is real, and **the only one a client shows**.\nWhere it is `null`, the item\'s `code` is something KApp generated so the system could\ntell the items apart; showing it would put an invented code in front of a student.':
    'El código de la materia tal como lo lleva el sistema propio de la universidad (SINU), o `null` donde no se ha confirmado.\n\nEs el único campo que dice que un código es real, y **el único que muestra un cliente**. Donde es `null`, el `code` del elemento es algo que generó KApp para que el sistema pudiera distinguir los elementos; mostrarlo pondría un código inventado frente a un estudiante.',
  '`true` when this item is a slot the student later resolves to a real course, rather\nthan a fixed course.':
    '`true` cuando este elemento es un espacio que el estudiante resuelve después con una materia real, en lugar de una materia fija.',
  'Codes of the courses that must all be `PASSED` before this item can be taken. Empty\nwhen the item has none. Every entry must be the `code` of another item of the same\npensum.':
    'Códigos de las materias que deben estar todas `PASSED` antes de poder cursar este elemento. Vacío cuando el elemento no tiene. Cada entrada debe ser el `code` de otro elemento del mismo pensum.',
  'Replace a pensum':
    'Reemplazar un pensum',
  'Full replacement of an existing pensum. Requires `ROLE_ADMIN`.\n\nThe `pensumCode` in the body must equal the one in the path; a mismatch is rejected with\n`400`.\n\nReplacing a pensum does **not** rewrite the progress documents that pin it. Students\nwhose plan diverged learn about it through the `reconciliation` block on\n`GET /api/semaphore/me`.':
    'Reemplazo completo de un pensum existente. Requiere `ROLE_ADMIN`.\n\nEl `pensumCode` del cuerpo debe ser igual al de la ruta; si no coincide, se rechaza con `400`.\n\nReemplazar un pensum **no** reescribe los documentos de progreso que lo fijan. Los estudiantes cuyo plan cambió se enteran por el bloque `reconciliation` de `GET /api/semaphore/me`.',
  'Delete a pensum':
    'Eliminar un pensum',
  'Requires `ROLE_ADMIN`. Refused with `409` while any student still follows it —\ndeleting it would leave their progress pointing at a pensum that no longer exists.':
    'Requiere `ROLE_ADMIN`. Se rechaza con `409` mientras algún estudiante lo siga: eliminarlo dejaría su progreso apuntando a un pensum que ya no existe.',
  'Create a pensum':
    'Crear un pensum',
  'Creates a new pensum. Requires `ROLE_ADMIN`.\n\nRejected with `400` when a course declares a prerequisite that is not itself a `code` in\nthe same document, when an elective slot carries a non-null `code`, when a non-slot item\ncarries a `null` code, when a course `area` is not one of the declared `areas`, or when a\nsupplied `totalHours` breaks the `weeklyHours * 16 == totalHours` invariant.\n\nRejected with `409` when `pensumCode` already exists — use\n`PUT /api/catalog/pensums/{pensumCode}` to replace it.':
    'Crea un pensum nuevo. Requiere `ROLE_ADMIN`.\n\nSe rechaza con `400` cuando una materia declara un prerrequisito que no es a su vez un `code` del mismo documento, cuando un espacio de electiva lleva un `code` no nulo, cuando un elemento que no es espacio lleva un código `null`, cuando el `area` de una materia no es una de las `areas` declaradas, o cuando un `totalHours` enviado rompe la regla `weeklyHours * 16 == totalHours`.\n\nSe rechaza con `409` cuando el `pensumCode` ya existe: use `PUT /api/catalog/pensums/{pensumCode}` para reemplazarlo.',
  'Import pensums in bulk from a CSV':
    'Importar pensums en bloque desde un CSV',
  'Requires `ROLE_ADMIN`. Loads whole pensums from a spreadsheet export — 24 programs and\nroughly 1200 rows is data-entry work, not programming, and this is what lets somebody do\nit without touching code.\n\nOne row per pensum item. The columns before `pensumItemCode` describe the pensum and are\nrepeated on every one of its rows; the import checks those repeated values agree with\neach other rather than letting the first row silently win. One file may carry several\npensums.\n\n**Nothing is written unless the whole file validates.** A partial import would leave the\ncatalogue in a state nobody chose, so every problem is collected first and reported\nagainst the line number of the file.\n\n**Declared totals are checked, not trusted.** The import adds up the courses and compares\nagainst `declaredCredits` and `declaredHours` — the latter being the sum of *weekly*\nhours, which is what a pensum\'s `totalHours` means. The seeded Ingeniería de Sistemas\nplan declares 142 and 194 where its courses give 144 and 197; accepting that silently\nwould put the discrepancy in front of a student months later.\n\n**Hours may carry a half**, in either `4.5` or the Spanish `4,5` — the latter has to be\nquoted, or it is not one cell but two. Any other fraction is refused against its row.\n\nColumn reference and a template: `docs/templates/`.':
    'Requiere `ROLE_ADMIN`. Carga pensums completos desde una hoja de cálculo exportada: 24 programas y unas 1200 filas son trabajo de digitación, no de programación, y esto es lo que permite que alguien lo haga sin tocar código.\n\nUna fila por elemento del pensum. Las columnas antes de `pensumItemCode` describen el pensum y se repiten en cada una de sus filas; la importación comprueba que esos valores repetidos coincidan entre sí en lugar de dejar que gane la primera fila en silencio. Un archivo puede traer varios pensums.\n\n**No se escribe nada a menos que todo el archivo sea válido.** Una importación parcial dejaría el catálogo en un estado que nadie eligió, así que primero se reúnen todos los problemas y se informan con el número de línea del archivo.\n\n**Los totales declarados se comprueban, no se creen.** La importación suma las materias y compara con `declaredCredits` y `declaredHours`; este último es la suma de horas *semanales*, que es lo que significa el `totalHours` de un pensum. El plan de Ingeniería de Sistemas que viene cargado declara 142 y 194 donde sus materias dan 144 y 197; aceptarlo en silencio pondría la diferencia frente a un estudiante meses después.\n\n**Las horas pueden llevar media**, como `4.5` o como `4,5` en español; esta última va entre comillas, o no es una celda sino dos. Cualquier otra fracción se rechaza en su fila.\n\nReferencia de columnas y una plantilla: `docs/templates/`.',
  'Validate the file and report what would happen, without writing anything.':
    'Validar el archivo e informar qué pasaría, sin escribir nada.',
  'What a bulk import did, or what a dry run would have done.':
    'Lo que hizo una importación en bloque, o lo que habría hecho una prueba en seco.',
  'When true, nothing was written.':
    'Cuando es verdadero, no se escribió nada.',
  'Data rows in the file, header excluded.':
    'Filas de datos del archivo, sin contar el encabezado.',
  'One entry per pensum the file described, whether or not it was written - a dry run reports the same shape.':
    'Una entrada por cada pensum que describía el archivo, se haya escrito o no; una prueba en seco informa con la misma forma.',
  'What the file states.':
    'Lo que dice el archivo.',
  'What its courses actually add up to. Equal to `declaredCredits`, or the import would have failed.':
    'Lo que suman de verdad sus materias. Igual a `declaredCredits`, o la importación habría fallado.',
  'False when the program already existed and was replaced.':
    'Falso cuando el programa ya existía y se reemplazó.',
  'False when the pensum already existed and was replaced.':
    'Falso cuando el pensum ya existía y se reemplazó.',
  'List the items of a pensum':
    'Listar los elementos de un pensum',
  'Returns the pensum items of one pensum, optionally narrowed by level, knowledge area,\nor elective-slot flag. Filters combine with AND. Omitting every filter returns the same\nlist as the `courses` array of `GET /api/catalog/pensums/{pensumCode}`.\n\nUseful for rendering one column of the grid (`?level=8`) or one row (`?area=ISA`), and for\nlisting the elective slots a student still has to resolve (`?isElectiveSlot=true`).':
    'Devuelve los elementos de un pensum, acotados si se quiere por nivel, área de conocimiento o indicador de espacio de electiva. Los filtros se combinan con Y. Sin ningún filtro devuelve la misma lista que el arreglo `courses` de `GET /api/catalog/pensums/{pensumCode}`.\n\nSirve para dibujar una columna de la cuadrícula (`?level=8`) o una fila (`?area=ISA`), y para listar los espacios de electiva que un estudiante aún tiene que resolver (`?isElectiveSlot=true`).',
  'Keep only items placed at this level (semester).':
    'Dejar solo los elementos ubicados en este nivel (semestre).',
  'Keep only items of this knowledge area, by area code.':
    'Dejar solo los elementos de esta área de conocimiento, por código de área.',
  'Keep only elective slots (`true`) or only fixed courses (`false`).':
    'Dejar solo los espacios de electiva (`true`) o solo las materias fijas (`false`).',
  'Get the caller\'s semáforo':
    'Obtener el semáforo de quien llama',
  'Returns the authenticated student\'s progress document.\n\n## Lazily created on first call\n\nA student never has to be provisioned. If no progress document exists yet, this call\nresolves the `ACTIVE` pensum of the student\'s `programCode`, pins that `pensumCode` on a\nnew document, and materialises one entry per pensum item with status `PENDING`. The\ndocument therefore always mirrors the pinned pensum one-to-one.\n\nBecause of that, **this endpoint does not return `404` for a student enrolled in a\nprogram**. `404` is reserved for a caller who has no active program, or whose program has\nno `ACTIVE` pensum to resolve.\n\n## Reconciliation\n\nThe document pins the `pensumCode` it was created from. If an administrator later edits\nthat pensum, the stored progress can diverge from it, so every response carries a\n`reconciliation` block. Nothing is ever silently dropped or invented:\n\n- `addedCourses` — pensum items present in the pensum but missing from the stored\n  document. They are materialised as `PENDING` and reported here, so the client can tell\n  the student their plan grew.\n- `removedCourses` — stored entries whose pensum item no longer exists in the pensum.\n  They are **kept** in `courses` (a passed grade is never destroyed) and reported here, so\n  the client can show them as no longer counting toward the degree. They are excluded from\n  `GET /api/semaphore/me/summary`.\n- `inSync` — `true` exactly when both arrays are empty.\n\nIdentifiers in both arrays are course `code`s, except for elective slots, which have no\n`code` and are reported by `pensumItemCode`.':
    'Devuelve el documento de progreso del estudiante autenticado.\n\n## Se crea al primer llamado\n\nA un estudiante nunca hay que darlo de alta. Si todavía no existe un documento de progreso, esta llamada resuelve el pensum `ACTIVE` del `programCode` del estudiante, fija ese `pensumCode` en un documento nuevo y crea una entrada por cada elemento del pensum con estado `PENDING`. Por eso el documento siempre refleja uno a uno el pensum fijado.\n\nPor lo mismo, **este endpoint no devuelve `404` para un estudiante matriculado en un programa**. `404` queda para quien llama sin un programa activo, o cuyo programa no tiene un pensum `ACTIVE` que resolver.\n\n## Conciliación\n\nEl documento fija el `pensumCode` del que se creó. Si un administrador edita después ese pensum, el progreso guardado puede desviarse de él, así que cada respuesta lleva un bloque `reconciliation`. Nunca se descarta ni se inventa nada en silencio:\n\n- `addedCourses`: elementos que están en el pensum pero faltan en el documento guardado. Se crean como `PENDING` y se informan aquí, para que el cliente le diga al estudiante que su plan creció.\n- `removedCourses`: entradas guardadas cuyo elemento ya no existe en el pensum. Se **conservan** en `courses` (una nota aprobada nunca se destruye) y se informan aquí, para que el cliente las muestre como que ya no cuentan para el título. Se excluyen de `GET /api/semaphore/me/summary`.\n- `inSync`: `true` exactamente cuando ambos arreglos están vacíos.\n\nLos identificadores de ambos arreglos son `code` de materias, salvo los espacios de electiva, que no tienen `code` y se informan por `pensumItemCode`.',
  'A student\'s semáforo together with its reconciliation status. Returned by\n`GET /api/semaphore/me`, which is the only caller that can reconcile the document against\nits pensum on read.':
    'El semáforo de un estudiante junto con su estado de conciliación. Lo devuelve `GET /api/semaphore/me`, que es el único que puede conciliar el documento con su pensum al leerlo.',
  'A student\'s semáforo: one entry per item of the pinned pensum.':
    'El semáforo de un estudiante: una entrada por cada elemento del pensum fijado.',
  'Identifier of the KApp user, taken from the JWT `sub` claim. An opaque string, not a number: it is a MongoDB ObjectId and must never be parsed as an integer.':
    'Identificador del usuario de KApp, tomado del claim `sub` del JWT. Es un texto opaco, no un número: es un ObjectId de MongoDB y nunca debe interpretarse como entero.',
  'Institutional student code.':
    'Código institucional del estudiante.',
  'The pensum this document is pinned to. It does not follow later edits of that\npensum on its own — see the `reconciliation` block of `GET /api/semaphore/me`.':
    'El pensum al que está fijado este documento. No sigue por sí solo las ediciones posteriores de ese pensum: vea el bloque `reconciliation` de `GET /api/semaphore/me`.',
  'The level (semester) the student is currently in.':
    'El nivel (semestre) en el que está el estudiante.',
  'One entry per item of the pinned pensum, plus any entry retained from a removed item.\nRoughly 51 entries for the Ingeniería de Sistemas Reforma 2018 plan; the examples in\nthis document show a representative slice.':
    'Una entrada por cada elemento del pensum fijado, más cualquier entrada que se conserve de un elemento quitado. Unas 51 entradas en el plan de Ingeniería de Sistemas Reforma 2018; los ejemplos de este documento muestran una parte representativa.',
  'One item of a student\'s semáforo: the status of a single pensum item, plus the elective\nresolution when the item is a slot.':
    'Un elemento del semáforo de un estudiante: el estado de un solo elemento del pensum, más la resolución de la electiva cuando el elemento es un espacio.',
  'Course code of the pensum item. `null` for an elective slot — even a resolved one,\nwhose real course is carried by `resolvedCode`.':
    'Código de materia del elemento del pensum. `null` para un espacio de electiva, incluso uno resuelto, cuya materia real lleva `resolvedCode`.',
  'Identifier of the pensum item this entry tracks.':
    'Identificador del elemento del pensum que sigue esta entrada.',
  'Stored status of one item on a student\'s semáforo.\n\nNote that *blocked* is **not** a stored status: it is derived from prerequisites, and is\nthe complement of `GET /api/semaphore/me/eligible` within the `PENDING` set.':
    'Estado guardado de un elemento en el semáforo de un estudiante.\n\n*Bloqueado* **no** es un estado guardado: se deduce de los prerrequisitos y es el complemento de `GET /api/semaphore/me/eligible` dentro del conjunto `PENDING`.',
  'Academic period in the university\'s own format: four-digit year followed by the semester,\n`1` or `2`. `"20262"` is the second semester of 2026. `null` when the item has not been\nscheduled yet.':
    'Periodo académico en el formato propio de la universidad: el año de cuatro cifras seguido del semestre, `1` o `2`. `"20262"` es el segundo semestre de 2026. `null` cuando el elemento todavía no se ha programado.',
  'Final mark on the university\'s `0..50` integer scale. `null` while the course has not\nsettled.':
    'Nota final en la escala entera `0..50` de la universidad. `null` mientras la materia no se haya definido.',
  'Code of the real course this elective slot was resolved to. Always `null` for a fixed\ncourse, and `null` for an unresolved slot.':
    'Código de la materia real con la que se resolvió este espacio de electiva. Siempre `null` para una materia fija, y `null` para un espacio sin resolver.',
  'Name of the real course this elective slot was resolved to, denormalised so a client\ncan render the grid without a catalog lookup. Always `null` for a fixed course.':
    'Nombre de la materia real con la que se resolvió este espacio de electiva, copiado aquí para que un cliente pueda dibujar la cuadrícula sin consultar el catálogo. Siempre `null` para una materia fija.',
  'When the document was last written.':
    'Cuándo se escribió el documento por última vez.',
  'How the stored progress document compares with the pensum it pins, after an\nadministrator edited that pensum. Nothing is dropped or invented silently: every\ndivergence is reported here.\n\nIdentifiers are course `code`s, except for elective slots, which have no `code` and are\nreported by `pensumItemCode`.':
    'Cómo se compara el documento de progreso guardado con el pensum que fija, después de que un administrador editó ese pensum. Nada se descarta ni se inventa en silencio: cada diferencia se informa aquí.\n\nLos identificadores son `code` de materias, salvo los espacios de electiva, que no tienen `code` y se informan por `pensumItemCode`.',
  'Items the pensum now has and the document did not. They have been materialised as\n`PENDING`.':
    'Elementos que ahora tiene el pensum y que el documento no tenía. Se crearon como `PENDING`.',
  'Entries the document holds whose pensum item no longer exists in the pensum. They\nare kept in `courses` so no grade is lost, and are excluded from the summary.':
    'Entradas del documento cuyo elemento ya no existe en el pensum. Se conservan en `courses` para que no se pierda ninguna nota, y se excluyen del resumen.',
  '`true` exactly when both arrays are empty.':
    '`true` exactamente cuando ambos arreglos están vacíos.',
  'Get the caller\'s progress summary':
    'Obtener el resumen de progreso de quien llama',
  'Credit totals derived from the caller\'s semáforo. **Never stored** — recomputed on every\ncall from the progress document and the pinned pensum, so it cannot drift from the\ngrid the student is looking at.\n\nCounting rules:\n\n- `creditsPassed` counts items with status `PASSED`.\n- `creditsInProgress` counts items with status `IN_PROGRESS`.\n- `creditsRemaining` is `totalCredits - creditsPassed - creditsInProgress`; `PENDING` and\n  `FAILED` items therefore both count as remaining.\n- Entries reported in `reconciliation.removedCourses` are excluded from every figure, as\n  they no longer belong to the plan.\n- `byArea` carries one row per area declared by the pensum, in the pensum\'s order.':
    'Totales de créditos deducidos del semáforo de quien llama. **Nunca se guardan**: se recalculan en cada llamada a partir del documento de progreso y del pensum fijado, así que no pueden desviarse de la cuadrícula que ve el estudiante.\n\nReglas de conteo:\n\n- `creditsPassed` cuenta los elementos con estado `PASSED`.\n- `creditsInProgress` cuenta los elementos con estado `IN_PROGRESS`.\n- `creditsRemaining` es `totalCredits - creditsPassed - creditsInProgress`; por eso los elementos `PENDING` y `FAILED` cuentan ambos como pendientes.\n- Las entradas informadas en `reconciliation.removedCourses` se excluyen de todas las cifras, porque ya no pertenecen al plan.\n- `byArea` trae una fila por cada área que declara el pensum, en el orden del pensum.',
  'Credit totals derived from a semáforo. Never stored — recomputed on every request.':
    'Totales de créditos deducidos de un semáforo. Nunca se guardan: se recalculan en cada solicitud.',
  'Credits the student has passed, across every knowledge area.':
    'Créditos que ha aprobado el estudiante, en todas las áreas de conocimiento.',
  'Credits the student is enrolled in right now - neither passed nor still to take.':
    'Créditos que el estudiante cursa en este momento: ni aprobados ni por cursar.',
  '`totalCredits - creditsPassed - creditsInProgress`.':
    '`totalCredits - creditsPassed - creditsInProgress`.',
  'Total credits of the pinned pensum.':
    'Total de créditos del pensum fijado.',
  '`creditsPassed / totalCredits * 100`, rounded to one decimal.':
    '`creditsPassed / totalCredits * 100`, redondeado a un decimal.',
  'One row per area of the pensum, in the pensum\'s order.':
    'Una fila por cada área del pensum, en el orden del pensum.',
  'Credit progress within one knowledge area.':
    'Progreso de créditos dentro de un área de conocimiento.',
  'Area code, as declared by the pensum.':
    'Código del área, tal como lo declara el pensum.',
  'Credits the student has passed in this knowledge area.':
    'Créditos que ha aprobado el estudiante en esta área de conocimiento.',
  'Credits the area holds in total, which is what the progress bar fills against.':
    'Créditos que tiene el área en total, contra los que se llena la barra de progreso.',
  'The semester the student is counted as being in. Derived from what they have passed, not from what they planned.':
    'El semestre en el que se cuenta al estudiante. Se deduce de lo que ha aprobado, no de lo que planeó.',
  'List the courses the caller can take next':
    'Listar las materias que quien llama puede cursar a continuación',
  'Returns the pensum items the student may enrol in right now: those whose prerequisites are\n**all** `PASSED` and whose own status is still `PENDING`. This is the computation the\n"traffic light" is named for — everything else `PENDING` is *blocked*, and a client can\npaint it as such by subtracting this list from the `PENDING` set.\n\nNotes:\n\n- Items with no prerequisites are eligible as soon as they are `PENDING`.\n- `FAILED` items are **not** returned. They are retakes rather than new enrolments, and are\n  already identifiable by their status on the progress document.\n- Unresolved elective slots are returned like any other item; resolve one with\n  `POST /api/semaphore/me/electives/{pensumItemCode}`.\n- Items are returned in pensum form (`PensumCourse`), not progress form, so the\n  client has credits, hours and area available without a second lookup.':
    'Devuelve los elementos del pensum en los que el estudiante puede inscribirse ahora: aquellos cuyos prerrequisitos están **todos** `PASSED` y cuyo propio estado sigue siendo `PENDING`. Es el cálculo que le da nombre al "semáforo": todo lo demás `PENDING` está *bloqueado*, y un cliente puede pintarlo así restando esta lista del conjunto `PENDING`.\n\nNotas:\n\n- Los elementos sin prerrequisitos son elegibles en cuanto están `PENDING`.\n- Los elementos `FAILED` **no** se devuelven. Son repeticiones y no inscripciones nuevas, y ya se identifican por su estado en el documento de progreso.\n- Los espacios de electiva sin resolver se devuelven como cualquier otro elemento; resuelva uno con `POST /api/semaphore/me/electives/{pensumItemCode}`.\n- Los elementos se devuelven en forma de pensum (`PensumCourse`), no de progreso, para que el cliente tenga créditos, horas y área sin otra consulta.',
  'Set the status of one course on the caller\'s semáforo':
    'Fijar el estado de una materia en el semáforo de quien llama',
  'Sets `status`, `grade` and `period` for a single item of the caller\'s semáforo and returns\nthe updated entry. The whole entry is replaced: omitting `grade` or `period` clears them.\n\nValidation — all rejected with `400`:\n\n- `grade` outside the university\'s `0..50` scale.\n- `status` of `PASSED` or `FAILED` without a `grade`. A settled course must carry the mark\n  it settled on.\n- `grade` supplied with a `status` of `PENDING`. A course that has not been taken has no\n  mark.\n- `period` that does not match `^\\d{4}[12]$`.\n\n`404` if the identifier does not name an item of the pensum pinned by the caller\'s\nprogress document.':
    'Fija `status`, `grade` y `period` de un solo elemento del semáforo de quien llama y devuelve la entrada actualizada. Se reemplaza la entrada completa: omitir `grade` o `period` los borra.\n\nValidación (todo se rechaza con `400`):\n\n- `grade` fuera de la escala `0..50` de la universidad.\n- `status` `PASSED` o `FAILED` sin `grade`. Una materia definida debe llevar la nota con la que se definió.\n- `grade` enviado con `status` `PENDING`. Una materia que no se ha cursado no tiene nota.\n- `period` que no cumple `^\\d{4}[12]$`.\n\n`404` si el identificador no nombra un elemento del pensum fijado por el documento de progreso de quien llama.',
  'Identifier of the pensum item. The course `code` for a fixed course, or — because an\nelective slot has no `code` — the slot\'s `pensumItemCode`.':
    'Identificador del elemento del pensum. El `code` de la materia para una materia fija o, como un espacio de electiva no tiene `code`, el `pensumItemCode` del espacio.',
  'Resolve an elective slot to a real course':
    'Resolver un espacio de electiva con una materia real',
  'Binds one of the caller\'s elective slots to the course the student actually took or\nenrolled in. The slot keeps its credits, hours, area and level — only its content is\ndecided — so `resolvedCode` and `resolvedName` are recorded on the progress entry while the\npensum stays untouched.\n\nCalling this on an already-resolved slot overwrites the previous resolution; use `DELETE`\nto clear it instead.\n\n`400` if `pensumItemCode` names an item that exists but is not an elective slot, or if\n`resolvedCode` names a course already accounted for elsewhere on the semáforo.\n`404` if the caller\'s pinned pensum has no such item.\n\nResolving a slot does not by itself set its status; follow with\n`PUT /api/semaphore/me/courses/{code}` using the slot\'s `pensumItemCode`.':
    'Liga uno de los espacios de electiva de quien llama con la materia que el estudiante cursó o inscribió de verdad. El espacio conserva sus créditos, horas, área y nivel (solo se decide su contenido), así que `resolvedCode` y `resolvedName` se guardan en la entrada de progreso y el pensum no se toca.\n\nLlamarlo sobre un espacio ya resuelto sobrescribe la resolución anterior; para borrarla use `DELETE`.\n\n`400` si `pensumItemCode` nombra un elemento que existe pero no es un espacio de electiva, o si `resolvedCode` nombra una materia que ya se cuenta en otra parte del semáforo. `404` si el pensum fijado de quien llama no tiene ese elemento.\n\nResolver un espacio no fija por sí solo su estado; después llame a `PUT /api/semaphore/me/courses/{code}` con el `pensumItemCode` del espacio.',
  'Stable identifier of the elective slot within the pensum.':
    'Identificador estable del espacio de electiva dentro del pensum.',
  'Clear an elective slot resolution':
    'Borrar la resolución de un espacio de electiva',
  'Returns the slot to an empty slot: `resolvedCode` and `resolvedName` go back to `null`.\nThe slot itself is never removed from the semáforo — it is part of the pensum.\n\nIdempotent: clearing an already-empty slot also returns `204`.\n\n`400` if `pensumItemCode` names an item that exists but is not an elective slot.\n`404` if the caller\'s pinned pensum has no such item.':
    'Devuelve el espacio a vacío: `resolvedCode` y `resolvedName` vuelven a `null`. El espacio en sí nunca se quita del semáforo: es parte del pensum.\n\nIdempotente: borrar un espacio que ya está vacío también devuelve `204`.\n\n`400` si `pensumItemCode` nombra un elemento que existe pero no es un espacio de electiva. `404` si el pensum fijado de quien llama no tiene ese elemento.',
  'Get another student\'s semáforo':
    'Obtener el semáforo de otro estudiante',
  'Administrative read of any student\'s progress document. Requires `ROLE_ADMIN`.\n\nUnlike `GET /api/semaphore/me`, this call is a plain read: it never creates a document\nlazily, and it carries no `reconciliation` block. `404` therefore means either that no such\nuser exists or that the student has never opened their semáforo.':
    'Lectura administrativa del documento de progreso de cualquier estudiante. Requiere `ROLE_ADMIN`.\n\nA diferencia de `GET /api/semaphore/me`, esta llamada es una lectura simple: nunca crea un documento al vuelo y no trae bloque `reconciliation`. Por eso `404` significa que ese usuario no existe o que el estudiante nunca ha abierto su semáforo.',
  'Identifier of the KApp user whose semáforo is being read. This is the `sub` claim of\nthe access token — a UUID string, never a number. A client generated from an earlier\nrevision of this file typed it as `int64`, which no real identifier has ever matched.':
    'Identificador del usuario de KApp cuyo semáforo se lee. Es el claim `sub` del token de acceso: un texto UUID, nunca un número. Un cliente generado a partir de una versión anterior de este archivo lo tipaba como `int64`, que nunca coincidió con ningún identificador real.',
  'List the student\'s academic plans':
    'Listar los planes académicos del estudiante',
  'Requires `ROLE_STUDENT`. Returns the caller\'s own plans, the primary one first.\n\nA plan is a layer over the pensum, never a copy of it. The pensum stays the\nimmutable *Semáforo Original*; a plan records only the courses the student has moved,\nwhich is why `placements` is usually short and often empty.':
    'Requiere `ROLE_STUDENT`. Devuelve los planes propios de quien llama, primero el principal.\n\nUn plan es una capa sobre el pensum, nunca una copia. El pensum sigue siendo el *Semáforo Original* inmutable; un plan solo guarda las materias que el estudiante movió, y por eso `placements` suele ser corto y muchas veces está vacío.',
  'A student\'s own arrangement of a pensum.\n\n`placements` carries **only the courses that moved**. Anything absent is taken where the\npensum puts it. That keeps the document small, makes the outline the client draws in\nthe original column fall out for free — it knows both levels — and means a change to the\npensum reaches every plan without rewriting any of them.':
    'El arreglo propio que hace un estudiante de un pensum.\n\n`placements` lleva **solo las materias que se movieron**. Lo que no está se toma donde lo pone el pensum. Así el documento es pequeño, el contorno que dibuja el cliente en la columna original sale solo (conoce ambos niveles) y un cambio en el pensum llega a todos los planes sin reescribir ninguno.',
  'The pensum this plan layers over. The plan stores only the moves; everything else is read from the pensum.':
    'El pensum sobre el que va este plan. El plan solo guarda los movimientos; todo lo demás se lee del pensum.',
  'The plan the app opens on. Exactly one per student and pensum.':
    'El plan con el que abre la app. Exactamente uno por estudiante y pensum.',
  'Only the courses the student moved.':
    'Solo las materias que movió el estudiante.',
  'One course pinned to a level other than the pensum\'s.':
    'Una materia fijada en un nivel distinto al del pensum.',
  'The course `code`, or an elective slot\'s `pensumItemCode` — the same identifier\n`PUT /api/semaphore/me/courses/{code}` accepts.':
    'El `code` de la materia, o el `pensumItemCode` de un espacio de electiva: el mismo identificador que acepta `PUT /api/semaphore/me/courses/{code}`.',
  'The level the student intends to take it in.':
    'El nivel en el que el estudiante piensa cursarla.',
  'When the student created the plan.':
    'Cuándo creó el estudiante el plan.',
  'When the plan last changed - a move, a rename, or being made primary.':
    'Cuándo cambió el plan por última vez: un movimiento, un cambio de nombre o volverse el principal.',
  'Create an academic plan':
    'Crear un plan académico',
  'Requires `ROLE_STUDENT`. The plan starts empty: every course sits where the pensum puts\nit until the student moves one.\n\nThe first plan a student creates for a pensum becomes their primary automatically.\nA student may hold at most 10 plans per pensum — enough for "what if I take Cálculo in\nthe summer", far short of an unbounded collection growing under one account.':
    'Requiere `ROLE_STUDENT`. El plan empieza vacío: cada materia está donde la pone el pensum hasta que el estudiante mueva una.\n\nEl primer plan que crea un estudiante para un pensum se vuelve su principal automáticamente. Un estudiante puede tener como máximo 10 planes por pensum: suficiente para "qué pasa si veo Cálculo en vacaciones", y muy lejos de una colección sin límite que crece bajo una cuenta.',
  'Get one academic plan':
    'Obtener un plan académico',
  'Requires `ROLE_STUDENT`. `404` for a plan belonging to somebody else, deliberately: a\n`403` would confirm that the identifier exists.':
    'Requiere `ROLE_STUDENT`. `404` para un plan de otra persona, a propósito: un `403` confirmaría que el identificador existe.',
  'Identifier of one of the caller\'s own academic plans.':
    'Identificador de uno de los planes académicos propios de quien llama.',
  'Rename a plan or make it the primary one':
    'Cambiar el nombre de un plan o volverlo el principal',
  'Requires `ROLE_STUDENT`. Both fields are optional; sending neither is a `400`.\n\nSetting `primary` to `true` demotes whichever plan currently holds it for the same\npensum — there is exactly one primary per student and pensum. Setting it to `false` is\nrejected: a student would be left with none, and something has to be the plan the app\nopens on.':
    'Requiere `ROLE_STUDENT`. Ambos campos son opcionales; no enviar ninguno es un `400`.\n\nPoner `primary` en `true` le quita el rol de principal al plan que lo tenga para el mismo pensum: hay exactamente un principal por estudiante y pensum. Ponerlo en `false` se rechaza: el estudiante quedaría sin ninguno, y algún plan tiene que ser con el que abre la app.',
  'Delete an academic plan':
    'Eliminar un plan académico',
  'Requires `ROLE_STUDENT`. Deleting the primary plan promotes the next one by creation\ndate; deleting the last plan for a pensum is allowed and simply leaves the student with\nthe pensum as published.':
    'Requiere `ROLE_STUDENT`. Eliminar el plan principal promueve el siguiente por fecha de creación; eliminar el último plan de un pensum está permitido y simplemente deja al estudiante con el pensum tal como se publicó.',
  'Move a course to a different level':
    'Mover una materia a otro nivel',
  'Requires `ROLE_STUDENT`. Idempotent: moving a course already at that level returns the\nplan unchanged.\n\n**Moving a course does not make it eligible.** `GET /api/semaphore/me/eligible` is\ncomputed from prerequisites actually approved, and it ignores plans entirely. Planning\nis not approving, and a plan that could unlock a course by dragging it would be a\nsemáforo that lies.\n\nThere is **no cap on how many courses may sit at one level**. Students take more or\nfewer than the nominal six all the time, and a limit here would refuse a real timetable.\n\n`400` when the course is not in the pensum, or when `plannedLevel` is outside 1–12.':
    'Requiere `ROLE_STUDENT`. Idempotente: mover una materia que ya está en ese nivel devuelve el plan sin cambios.\n\n**Mover una materia no la vuelve elegible.** `GET /api/semaphore/me/eligible` se calcula con los prerrequisitos realmente aprobados e ignora los planes por completo. Planear no es aprobar, y un plan que pudiera desbloquear una materia arrastrándola sería un semáforo que miente.\n\n**No hay un tope de materias por nivel**. Los estudiantes ven más o menos de las seis nominales todo el tiempo, y un límite aquí rechazaría un horario real.\n\n`400` cuando la materia no está en el pensum, o cuando `plannedLevel` está fuera de 1–12.',
  'Return a course to the level the pensum gives it':
    'Devolver una materia al nivel que le da el pensum',
  'Requires `ROLE_STUDENT`. Removes the placement, so the course goes back to its published\nlevel. `404` when the plan has no placement for that course — it was never moved.':
    'Requiere `ROLE_STUDENT`. Quita la ubicación, así que la materia vuelve a su nivel publicado. `404` cuando el plan no tiene ubicación para esa materia: nunca se movió.',
  // schedule.openapi.yaml
  'Get the caller\'s schedule for a period':
    'Obtener el horario de quien llama para un periodo',
  'Returns the caller\'s schedule, with every enrollment, meeting and meeting period nested\ninside it. When `period` is omitted the active schedule is returned.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Devuelve el horario de quien llama, con cada inscripción, sesión y rango de sesión anidados dentro. Cuando se omite `period`, se devuelve el horario activo.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Academic period to read. When omitted, the active schedule is returned.':
    'Periodo académico que se lee. Si se omite, se devuelve el horario activo.',
  'A student\'s whole timetable for one academic period. There is at most one schedule per\nstudent per period.':
    'El horario completo de un estudiante para un periodo académico. Hay como máximo un horario por estudiante y periodo.',
  'Server-assigned identifier of the schedule.':
    'Identificador del horario, asignado por el servidor.',
  'The university\'s own academic period code: four digits for the year, then `1` or `2` for the\nsemester. `"20262"` is the second semester of 2026.':
    'El código propio de periodo académico de la universidad: cuatro cifras para el año y luego `1` o `2` para el semestre. `"20262"` es el segundo semestre de 2026.',
  'Code of the academic program the student is enrolled in.':
    'Código del programa académico en el que está matriculado el estudiante.',
  'Code of the study plan (pensum) version the student follows.':
    'Código de la versión del plan de estudios (pensum) que sigue el estudiante.',
  'The semester the student is in during this period.':
    'El semestre en el que está el estudiante durante este periodo.',
  'Whether this is the student\'s current schedule. Exactly one schedule is active at a\ntime; it is the one served when `period` is omitted.':
    'Si este es el horario actual del estudiante. Hay exactamente un horario activo a la vez; es el que se entrega cuando se omite `period`.',
  'The courses the student takes during this period. Empty right after creation.':
    'Las materias que cursa el estudiante durante este periodo. Vacío justo después de crearlo.',
  'One course the student takes during the period, with its weekly meetings.\n\n`courseName`, `credits`, `professor`, `totalHours`, `level`, `group`, `subgroup` and\n`campus` are **snapshots taken when the enrollment was written, not live references to the\ncourse catalogue**. A timetable entry is a historical record: renaming a course, changing\nits credits or reassigning its professor later must not alter entries already stored.':
    'Una materia que cursa el estudiante durante el periodo, con sus sesiones semanales.\n\n`courseName`, `credits`, `professor`, `totalHours`, `level`, `group`, `subgroup` y `campus` son **copias tomadas cuando se escribió la inscripción, no referencias vivas al catálogo de materias**. Una entrada del horario es un registro histórico: cambiar después el nombre de una materia, sus créditos o su profesor no debe alterar las entradas ya guardadas.',
  'Server-assigned identifier of the enrollment.':
    'Identificador de la inscripción, asignado por el servidor.',
  'The university\'s course code, as printed on the SINU report.':
    'El código de la materia de la universidad, tal como aparece en el reporte del SINU.',
  'Code of the matching item in the study plan (pensum).':
    'Código del elemento correspondiente en el plan de estudios (pensum).',
  'Course name at write time. Snapshot, not a live reference.':
    'Nombre de la materia al momento de escribirla. Es una copia, no una referencia viva.',
  'The semester the course belongs to in the study plan.':
    'El semestre al que pertenece la materia en el plan de estudios.',
  'Academic credits at write time. Snapshot, not a live reference.':
    'Créditos académicos al momento de escribirla. Es una copia, no una referencia viva.',
  'Total hours of the course over the semester, as printed on the report.':
    'Total de horas de la materia en el semestre, tal como aparece en el reporte.',
  'Group code the student is enrolled in.':
    'Código del grupo en el que está inscrito el estudiante.',
  'Subgroup code, or `null` when the group is not subdivided.':
    'Código del subgrupo, o `null` cuando el grupo no se divide.',
  'Professor\'s full name at write time, in the report\'s own uppercase surname-first form.\nSnapshot, not a live reference.':
    'Nombre completo del profesor al momento de escribirla, en la forma propia del reporte: mayúsculas y primero el apellido. Es una copia, no una referencia viva.',
  'Campus the course is taught at.':
    'Sede en la que se dicta la materia.',
  'First day of the course over the whole semester, inclusive. Equal to the earliest\n`from` across all of this enrollment\'s meeting periods.':
    'Primer día de la materia en todo el semestre, incluido. Igual al `from` más temprano de todos los rangos de sesión de esta inscripción.',
  'Last day of the course over the whole semester, inclusive. Equal to the latest `to`\nacross all of this enrollment\'s meeting periods.':
    'Último día de la materia en todo el semestre, incluido. Igual al `to` más tardío de todos los rangos de sesión de esta inscripción.',
  'Colour the client paints this course with in the timetable, as `#RRGGBB`.':
    'Color con el que el cliente pinta esta materia en el horario, como `#RRGGBB`.',
  'The weekly slots this course is taught in. May be empty.':
    'Las franjas semanales en las que se dicta esta materia. Puede estar vacío.',
  'One weekly day + time slot of a course, together with the date ranges over which it is\nactually taught and the room in force during each of them.':
    'Una franja semanal de día y hora de una materia, junto con los rangos de fechas en los que realmente se dicta y el salón vigente en cada uno.',
  'Server-assigned identifier of the weekly slot.':
    'Identificador de la franja semanal, asignado por el servidor.',
  'The weekday a meeting repeats on. Weeks run Monday to Sunday.':
    'El día de la semana en que se repite una sesión. Las semanas van de lunes a domingo.',
  'When the class starts. Inclusive.':
    'Cuándo empieza la clase. Incluido.',
  'A time of day as `HH:mm` on a 24-hour clock. No seconds, no timezone.':
    'Una hora del día como `HH:mm` en formato de 24 horas. Sin segundos ni zona horaria.',
  'When the class ends. Exclusive for overlap purposes, so a class ending at `20:30` does\nnot conflict with one starting at `20:30`. Always later than `startTime`.':
    'Cuándo termina la clase. Excluido para efectos de cruce, así que una clase que termina a las `20:30` no se cruza con una que empieza a las `20:30`. Siempre es posterior a `startTime`.',
  'The disjoint date ranges over which this weekly slot is taught, in ascending order of\n`from`. Hand-entered schedules carry exactly one range covering the whole semester;\nSINU-synced schedules carry several.':
    'Los rangos de fechas disjuntos en los que se dicta esta franja semanal, en orden ascendente de `from`. Los horarios ingresados a mano llevan exactamente un rango que cubre todo el semestre; los sincronizados con el SINU llevan varios.',
  'One contiguous stretch of the semester during which a weekly meeting is taught in a given\nroom. Both endpoints are **inclusive**, and a single-day range has `from` equal to `to`.\n\nA meeting\'s periods are disjoint and must not overlap each other; the gaps between them are\nthe weeks the class does not meet.':
    'Un tramo continuo del semestre durante el cual una sesión semanal se dicta en un salón dado. Ambos extremos están **incluidos**, y un rango de un solo día tiene `from` igual a `to`.\n\nLos rangos de una sesión son disjuntos y no deben cruzarse entre sí; los huecos entre ellos son las semanas en que la clase no se reúne.',
  'First date of the range, inclusive. Falls on the meeting\'s `dayOfWeek`.':
    'Primera fecha del rango, incluida. Cae en el `dayOfWeek` de la sesión.',
  'Last date of the range, inclusive. Falls on the meeting\'s `dayOfWeek`, and is never\nearlier than `from`.':
    'Última fecha del rango, incluida. Cae en el `dayOfWeek` de la sesión y nunca es anterior a `from`.',
  'A classroom code, whose first digit is the floor: `"302"` is on the third floor. These are\nthe same space codes the KApp map service uses, so a client can jump from a class to its\nclassroom on the floor plan.\n\n`null` means no classroom has been assigned for that stretch of the semester. It is a real,\ncommon state in the source report, not a missing value, and clients must render it.':
    'El código de un salón, cuyo primer dígito es el piso: `"302"` está en el tercer piso. Son los mismos códigos de espacio que usa el servicio de mapa de KApp, así que un cliente puede saltar de una clase a su salón en el plano del piso.\n\n`null` significa que no se ha asignado salón para ese tramo del semestre. Es un estado real y común en el reporte de origen, no un valor faltante, y los clientes deben mostrarlo.',
  'Create the caller\'s schedule for a period':
    'Crear el horario de quien llama para un periodo',
  'Creates an empty schedule for one academic period. Courses are added afterwards through\n`POST /api/schedule/me/enrollments`.\n\nA student may hold at most one schedule per period.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Crea un horario vacío para un periodo académico. Las materias se agregan después con `POST /api/schedule/me/enrollments`.\n\nUn estudiante puede tener como máximo un horario por periodo.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Delete the caller\'s schedule for a period':
    'Eliminar el horario de quien llama para un periodo',
  'Deletes the schedule for the given period together with all of its enrollments, meetings\nand meeting periods. `period` is required: there is no "delete whatever is active" shortcut,\nbecause the operation is destructive.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Elimina el horario del periodo indicado junto con todas sus inscripciones, sesiones y rangos de sesión. `period` es obligatorio: no hay un atajo para "eliminar lo que esté activo", porque la operación es destructiva.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Academic period to delete. Required, because the operation is destructive.':
    'Periodo académico que se elimina. Obligatorio, porque la operación es destructiva.',
  'List the periods the caller has a schedule for':
    'Listar los periodos para los que quien llama tiene horario',
  'Returns one entry per stored schedule, newest period first. Clients use it to populate the\nperiod switcher without downloading every schedule.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Devuelve una entrada por cada horario guardado, primero el periodo más reciente. Los clientes lo usan para llenar el selector de periodo sin descargar todos los horarios.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'One row of the period switcher.':
    'Una fila del selector de periodo.',
  'Whether this is the student\'s current period.':
    'Si este es el periodo actual del estudiante.',
  'How many courses the schedule for that period holds.':
    'Cuántas materias tiene el horario de ese periodo.',
  'Add a course to the caller\'s schedule':
    'Agregar una materia al horario de quien llama',
  'Adds one course, together with its weekly meetings, to the caller\'s active schedule.\n\nEvery meeting in `meetings[]` is checked against every meeting already stored in the\nschedule, and against the other meetings in the same request, using the overlap rule\ndescribed at the top of this document. If any of them conflicts, nothing is written and the\nrequest fails with `409`.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Agrega una materia, junto con sus sesiones semanales, al horario activo de quien llama.\n\nCada sesión de `meetings[]` se compara con todas las sesiones ya guardadas en el horario, y con las demás sesiones de la misma solicitud, usando la regla de cruce descrita al comienzo de este documento. Si alguna se cruza, no se escribe nada y la solicitud falla con `409`.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Partially update an enrollment':
    'Actualizar parcialmente una inscripción',
  'Updates the enrollment\'s own fields. Only the keys present in the body are changed; an\nexplicit `null` clears a nullable field (`subgroup`).\n\nMeetings are **not** editable here — the body rejects a `meetings` key with `400`. Use the\n`/meetings` sub-resource instead. Because nothing about the weekly slots can change through\nthis operation, it can never produce an overlap.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Actualiza los campos propios de la inscripción. Solo cambian las claves presentes en el cuerpo; un `null` explícito borra un campo que admite nulo (`subgroup`).\n\nLas sesiones **no** se editan aquí: el cuerpo rechaza una clave `meetings` con `400`. Use el subrecurso `/meetings`. Como con esta operación no puede cambiar nada de las franjas semanales, nunca puede producir un cruce.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Identifier of the enrollment, as returned in `Enrollment.enrollmentId`.':
    'Identificador de la inscripción, tal como se devuelve en `Enrollment.enrollmentId`.',
  'Remove a course from the caller\'s schedule':
    'Quitar una materia del horario de quien llama',
  'Deletes the enrollment together with all of its meetings and meeting periods.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Elimina la inscripción junto con todas sus sesiones y rangos de sesión.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Add a weekly slot to an enrollment':
    'Agregar una franja semanal a una inscripción',
  'Adds one weekly day + time slot, with its date ranges and rooms, to an existing enrollment.\n\nThe new meeting is checked against every other meeting in the caller\'s schedule using the\noverlap rule described at the top of this document; on conflict nothing is written and the\nrequest fails with `409`.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Agrega una franja semanal de día y hora, con sus rangos de fechas y salones, a una inscripción existente.\n\nLa nueva sesión se compara con todas las demás sesiones del horario de quien llama usando la regla de cruce descrita al comienzo de este documento; si se cruza, no se escribe nada y la solicitud falla con `409`.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Partially update a weekly slot':
    'Actualizar parcialmente una franja semanal',
  'Updates a weekly slot. Only the keys present in the body are changed.\n\n`periods` is replaced wholesale when present: send the complete list of date ranges you want\nthe meeting to end up with, not a delta. This keeps the disjoint-ranges list unambiguous.\n\nThe resulting meeting is re-checked against every other meeting in the caller\'s schedule\nusing the overlap rule described at the top of this document; on conflict nothing is written\nand the request fails with `409`.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Actualiza una franja semanal. Solo cambian las claves presentes en el cuerpo.\n\n`periods` se reemplaza completo cuando está presente: envíe la lista completa de rangos de fechas con la que quiere que quede la sesión, no un cambio parcial. Así la lista de rangos disjuntos no deja lugar a dudas.\n\nLa sesión resultante se vuelve a comparar con todas las demás sesiones del horario de quien llama usando la regla de cruce descrita al comienzo de este documento; si se cruza, no se escribe nada y la solicitud falla con `409`.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Identifier of the weekly slot, as returned in `Meeting.meetingId`.':
    'Identificador de la franja semanal, tal como se devuelve en `Meeting.meetingId`.',
  'Remove a weekly slot from an enrollment':
    'Quitar una franja semanal de una inscripción',
  'Deletes the meeting together with all of its date ranges. The enrollment itself is kept even\nif this was its last meeting, so that a slot can be re-entered without retyping the course.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Elimina la sesión junto con todos sus rangos de fechas. La inscripción se conserva aunque esta fuera su última sesión, para que una franja se pueda volver a ingresar sin reescribir la materia.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Get the caller\'s classes on one date':
    'Obtener las clases de quien llama en una fecha',
  'Resolves which meetings actually take place on `date`. For every meeting in the caller\'s\nschedule the server checks the meeting\'s `dayOfWeek` against the weekday of `date`, then\nwalks the meeting\'s `periods[]` for a range that contains `date` (both endpoints inclusive).\nMatching meetings are returned with the room in force during that range, which may be\n`null`.\n\nThe result is sorted by `startTime` ascending. An empty array means no classes that day.\n\n**This is the endpoint the mobile home screen calls.**\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Resuelve qué sesiones ocurren realmente en `date`. Para cada sesión del horario de quien llama, el servidor compara su `dayOfWeek` con el día de la semana de `date` y luego recorre sus `periods[]` buscando un rango que contenga `date` (ambos extremos incluidos). Las sesiones que coinciden se devuelven con el salón vigente en ese rango, que puede ser `null`.\n\nEl resultado se ordena por `startTime` ascendente. Un arreglo vacío significa que ese día no hay clases.\n\n**Este es el endpoint que llama la pantalla de inicio de la app móvil.**\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'The calendar date to resolve, as `YYYY-MM-DD`.':
    'La fecha del calendario que se resuelve, como `YYYY-MM-DD`.',
  'One class actually taking place on one concrete date, already resolved by the server: the\nmeeting matched the date\'s weekday, one of its periods contained the date, and `room` is the\nroom in force during that period.':
    'Una clase que ocurre realmente en una fecha concreta, ya resuelta por el servidor: la sesión coincidió con el día de la semana de la fecha, uno de sus rangos contenía la fecha, y `room` es el salón vigente en ese rango.',
  'The enrollment this class belongs to, for drilling into the course detail.':
    'La inscripción a la que pertenece esta clase, para entrar al detalle de la materia.',
  'RGB hex the client draws this class in, chosen by the student so their week is readable at a glance.':
    'Color RGB hexadecimal con el que el cliente dibuja esta clase, elegido por el estudiante para que su semana se lea de un vistazo.',
  'Get the caller\'s classes for one week':
    'Obtener las clases de quien llama en una semana',
  'Resolves a whole week at once, using exactly the same rule as\n`GET /api/schedule/me/day` applied to each of the seven dates.\n\n`date` may be **any** date inside the wanted week; the server snaps it to the Monday of that\nweek. Weeks run Monday to Sunday. When `date` is omitted the current week is returned.\n\nAll seven day keys are always present; a day with no classes carries an empty array. Each\nday\'s classes are sorted by `startTime` ascending. `weekStart` and `weekEnd` tell the client\nwhich calendar dates the seven buckets correspond to, so day, 2-day and week views can all\nbe rendered from a single call.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Resuelve una semana completa de una vez, con exactamente la misma regla de `GET /api/schedule/me/day` aplicada a cada una de las siete fechas.\n\n`date` puede ser **cualquier** fecha dentro de la semana que se quiere; el servidor la lleva al lunes de esa semana. Las semanas van de lunes a domingo. Cuando se omite `date`, se devuelve la semana actual.\n\nLas siete claves de día siempre están presentes; un día sin clases lleva un arreglo vacío. Las clases de cada día se ordenan por `startTime` ascendente. `weekStart` y `weekEnd` le dicen al cliente a qué fechas corresponden los siete grupos, así que las vistas de día, de 2 días y de semana se pueden dibujar con una sola llamada.\n\nRoles: `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Any date inside the wanted week, as `YYYY-MM-DD`; the server snaps it to that week\'s Monday.\nWhen omitted, the current week is returned.':
    'Cualquier fecha dentro de la semana que se quiere, como `YYYY-MM-DD`; el servidor la lleva al lunes de esa semana. Si se omite, se devuelve la semana actual.',
  'One Monday-to-Sunday week of resolved classes. All seven day keys are always present, and a\nday with no classes carries an empty array.':
    'Una semana de lunes a domingo con las clases resueltas. Las siete claves de día siempre están presentes, y un día sin clases lleva un arreglo vacío.',
  'The Monday of the returned week, inclusive.':
    'El lunes de la semana devuelta, incluido.',
  'The Sunday of the returned week, inclusive.':
    'El domingo de la semana devuelta, incluido.',
  'Classes grouped by weekday, each list sorted by `startTime` ascending.':
    'Las clases agrupadas por día de la semana, cada lista ordenada por `startTime` ascendente.',
  'Get any student\'s schedule':
    'Obtener el horario de cualquier estudiante',
  'Administrative read access to another user\'s schedule. The payload is identical to\n`GET /api/schedule/me`. When `period` is omitted the user\'s active schedule is returned.\n\nThe literal path `/api/schedule/me` always wins over this template, so `me` can never be\nread as a user id.\n\nRoles: `ROLE_ADMIN` only. Every other role, including `ROLE_STUDENT` asking for their own\nid, is rejected with `403`; students use the `/me` family instead.':
    'Acceso administrativo de lectura al horario de otro usuario. El contenido es idéntico al de `GET /api/schedule/me`. Cuando se omite `period`, se devuelve el horario activo del usuario.\n\nLa ruta literal `/api/schedule/me` siempre gana sobre esta plantilla, así que `me` nunca se puede leer como id de usuario.\n\nRoles: solo `ROLE_ADMIN`. Cualquier otro rol, incluido `ROLE_STUDENT` pidiendo su propio id, se rechaza con `403`; los estudiantes usan la familia `/me`.',
  'Identifier of the student whose schedule is being read.':
    'Identificador del estudiante cuyo horario se lee.',
  // map.openapi.yaml
  'Its street address as people write it. Absent when nobody has said.':
    'Su dirección, como la escribe la gente. No aparece cuando nadie la ha dicho.',
  'Its street address as people write it. Omitted on PUT, the stored one is kept; empty clears it.':
    'Su dirección, como la escribe la gente. Si se omite en un PUT, se conserva la guardada; vacía, la borra.',
  'List buildings':
    'Listar los edificios',
  'Every building with its wings and floors, ordered by code. `q` matches the code, the name\nand every alias, ignoring case and accents.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Todos los edificios con sus alas y pisos, ordenados por código. `q` coincide con el código, el nombre y cada alias, sin distinguir mayúsculas ni tildes.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Restrict results to one campus (Sede), matched exactly.':
    'Limitar los resultados a una sede, con coincidencia exacta.',
  'Code, name or alias, ignoring case and accents.':
    'Código, nombre o alias, sin distinguir mayúsculas ni tildes.',
  'Other names people use for the building.':
    'Otros nombres con los que la gente llama al edificio.',
  'One arm of a building, as that building names it.':
    'Un brazo de un edificio, como lo llama ese edificio.',
  'What the doors in this wing append to the room number. Absent when they append nothing.':
    'Lo que las puertas de esta ala le agregan al número del salón. No aparece cuando no agregan nada.',
  'How to get into or across this wing, where that is not obvious.':
    'Cómo entrar o atravesar esta ala, cuando no es obvio.',
  'Ordered by ascending level.':
    'Ordenados por nivel ascendente.',
  'One floor of a building, described as a drawing the client scales and draws.':
    'Un piso de un edificio, descrito como un dibujo que el cliente escala y dibuja.',
  'Identifier within the building, and the path segment the API uses. `S1` basement, `P0`, `P1`, `MEZZ`, `T` terrace.':
    'Identificador dentro del edificio, y el segmento de ruta que usa la API. `S1` sótano, `P0`, `P1`, `MEZZ`, `T` terraza.',
  'Vertical order only. Decimal so a mezzanine sits between its floors.':
    'Solo el orden vertical. Decimal para que un mezzanine quede entre sus pisos.',
  '`UNMAPPED` - the floor exists, nothing is drawn. `DRAFT` - drawn from photographs of posted\nplans, right in shape, unconfirmed in use. `VERIFIED` - walked and corrected on site.':
    '`UNMAPPED`: el piso existe, no hay nada dibujado. `DRAFT`: dibujado a partir de fotos de los planos publicados, con la forma correcta y el uso sin confirmar. `VERIFIED`: recorrido y corregido en el sitio.',
  'Whether a floor or a space is reachable without stairs. `UNKNOWN` is the default and\n**must not be shown as either answer** - most of the campus has not been checked yet.':
    'Si a un piso o a un espacio se puede llegar sin escaleras. `UNKNOWN` es el valor por defecto y **no debe mostrarse como ninguna de las dos respuestas**: la mayor parte del campus todavía no se ha revisado.',
  'How to get here when it is not obvious.':
    'Cómo llegar aquí cuando no es obvio.',
  'The drawing\'s width, in its own units. Every point on the floor lies within it.':
    'El ancho del dibujo, en sus propias unidades. Todo punto del piso queda dentro de él.',
  'A direction on the ground. A floor\'s `top` says which one its drawing\'s top edge faces, so a\nclient can turn the floor north up or draw a compass that points the right way. Turning a\ndrawing a quarter turn clockwise brings what was on its left to the top.':
    'Una dirección sobre el terreno. El `top` de un piso dice hacia cuál mira el borde superior de su dibujo, para que un cliente pueda girar el piso con el norte arriba o dibujar una brújula que apunte bien. Girar un dibujo un cuarto de vuelta en el sentido de las agujas del reloj lleva arriba lo que estaba a su izquierda.',
  'The building\'s walls around this floor, corners in order. Empty until traced.':
    'Los muros del edificio alrededor de este piso, con las esquinas en orden. Vacío hasta que se traza.',
  'A point on a floor\'s drawing, in the floor\'s own units. Origin at the top-left corner as\nthe plan hangs; `x` to the right, `y` down.':
    'Un punto del dibujo de un piso, en las unidades del piso. El origen está en la esquina superior izquierda tal como cuelga el plano; `x` hacia la derecha, `y` hacia abajo.',
  'A walkable route across a floor, drawn as a coloured line.':
    'Una ruta que se puede caminar a lo largo de un piso, dibujada como una línea de color.',
  'CSS hex colour, matching what is painted on the walls where the building colour-codes its wings.':
    'Color hexadecimal de CSS, igual al que está pintado en los muros donde el edificio distingue sus alas por color.',
  'The points the corridor runs through, in walking order. Must stay on the floor.':
    'Los puntos por los que pasa el pasillo, en el orden en que se camina. Debe quedar dentro del piso.',
  'Bumped by every layout save. Send it back with the next one; ignored when a whole building is written.':
    'Sube con cada guardado del plano. Devuélvalo con el siguiente; se ignora cuando se escribe un edificio completo.',
  'Where a building\'s drawing lies on the ground: its top-left corner, the bearing its top\nedge faces and how long one unit of it is. Its floors\' `top` is the same bearing to the\nnearest quarter turn.':
    'Dónde queda sobre el terreno el dibujo de un edificio: su esquina superior izquierda, el rumbo hacia el que mira su borde superior y cuánto mide una unidad. El `top` de sus pisos es ese mismo rumbo, redondeado al cuarto de vuelta más cercano.',
  'A point on the earth, in degrees of WGS 84.':
    'Un punto de la Tierra, en grados WGS 84.',
  'Degrees clockwise from true north that the drawing\'s top edge faces.':
    'Grados en el sentido de las agujas del reloj desde el norte verdadero hacia los que mira el borde superior del dibujo.',
  'How long one unit of the drawing is on the ground, in metres.':
    'Cuánto mide una unidad del dibujo sobre el terreno, en metros.',
  'The building from above, part by part, as the cadastre records it. Empty until somebody takes it from there.':
    'El edificio visto desde arriba, parte por parte, como lo registra el catastro. Vacío hasta que alguien lo toma de ahí.',
  'One part of a building from above, as the city\'s cadastre records it or, where the\ncadastre falls short, as it was found on site. A building is seldom one block - the\nEdificio Central is a strip of five floors along the Calle 63, a core of eight and a south\nwing over its auditorium - and each part rises its own number of floors.':
    'Una parte de un edificio vista desde arriba, como la registra el catastro de la ciudad o, donde el catastro se queda corto, como se encontró en el sitio. Un edificio pocas veces es un solo bloque (el Edificio Central es una franja de cinco pisos sobre la Calle 63, un núcleo de ocho y un ala sur sobre su auditorio) y cada parte sube su propio número de pisos.',
  'The cadastral lot the part stands on. Absent for a part the cadastre does not record, found on site.':
    'El lote catastral sobre el que está la parte. No aparece para una parte que el catastro no registra, encontrada en el sitio.',
  'How many floors it rises above the street: the cadastre\'s count, unless checked on site.':
    'Cuántos pisos sube sobre la calle: el conteo del catastro, salvo que se haya revisado en el sitio.',
  'The lowest floor above the street it takes in, when it is not the first: 2 for what the upper floors carry out over a portico or a sidewalk, which the cadastre, seeing from above, draws as if it reached the ground. Absent for a part that stands on the street.':
    'El piso más bajo sobre la calle que abarca, cuando no es el primero: 2 para lo que los pisos de arriba sacan sobre un pórtico o un andén, que el catastro, al verlo desde arriba, dibuja como si llegara al suelo. No aparece para una parte que está sobre la calle.',
  'How many it goes below.':
    'Cuántos baja.',
  'The code of the building\'s wing it belongs to, when known.':
    'El código del ala del edificio a la que pertenece, cuando se sabe.',
  'The outline of an area, closed - its first point repeated at the end.':
    'El contorno de un área, cerrado: su primer punto se repite al final.',
  'A point as GeoJSON writes it, `[lon, lat]`.':
    'Un punto como lo escribe GeoJSON, `[lon, lat]`.',
  'When the service produced this error, in UTC.':
    'Cuándo produjo el servicio este error, en UTC.',
  'The status\'s standard name - `Bad Request`, `Conflict`. For developers.':
    'El nombre estándar del estado: `Bad Request`, `Conflict`. Para desarrolladores.',
  'The path that produced the error.':
    'La ruta que produjo el error.',
  'Field-by-field reasons. `field` names what was wrong - in a layout save, with the\nposition of the space: `spaces[3].shape` - and `issue` says what about it.':
    'Los motivos campo por campo. `field` nombra lo que estaba mal (en un guardado del plano, con la posición del espacio: `spaces[3].shape`) e `issue` dice qué tiene.',
  'Create a building':
    'Crear un edificio',
  'Registers a new building with its wings and floors. The `code` must be unique across the\nmap; floor codes and levels, and wing codes, must be unique within the building.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'Registra un edificio nuevo con sus alas y pisos. El `code` debe ser único en todo el mapa; los códigos y niveles de los pisos, y los códigos de las alas, deben ser únicos dentro del edificio.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  'Get a building by code':
    'Obtener un edificio por código',
  'Allowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Roles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Building code.':
    'Código del edificio.',
  'Replace a building':
    'Reemplazar un edificio',
  'Replaces the building, wings and floors included: a floor or a wing omitted here is\ndeleted, and one that still has spaces is refused with `409`. Each floor keeps its\n`version`, which belongs to the layout save. A changed code, campus, floor level or wing\nsuffix is copied onto every space of the building.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'Reemplaza el edificio, alas y pisos incluidos: un piso o un ala que se omita aquí se elimina, y uno que todavía tenga espacios se rechaza con `409`. Cada piso conserva su `version`, que le pertenece al guardado del plano. Un cambio de código, sede, nivel de piso o sufijo de ala se copia a todos los espacios del edificio.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  'Delete a building':
    'Eliminar un edificio',
  'The building must be empty: one that still has spaces is refused with `409` and nothing\nis deleted. Never cascades.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'El edificio debe estar vacío: uno que todavía tenga espacios se rechaza con `409` y no se elimina nada. Nunca borra en cascada.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  'Get a floor and every space on it':
    'Obtener un piso y todos sus espacios',
  'The call that draws a floor: a drawing `width` x `height` units, origin at the top left,\nwith the building\'s `outline`, each space as the polygon in its `shape` with its `doors`\non it, and the corridors along their paths. Spaces without `shape` are on the floor but not\ndrawn yet - list them beside the drawing rather than dropping them. The building\'s wings\ncome along so each space\'s `wing` code can be shown by name.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'La llamada que dibuja un piso: un dibujo de `width` x `height` unidades, con el origen arriba a la izquierda, con el `outline` del edificio, cada espacio como el polígono de su `shape` con sus `doors` encima, y los pasillos a lo largo de sus recorridos. Los espacios sin `shape` están en el piso pero todavía no se han dibujado: lístelos junto al dibujo en lugar de descartarlos. Las alas del edificio vienen incluidas para que el código `wing` de cada espacio se pueda mostrar por su nombre.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Floor code within the building - `S1`, `P0`, `P1`, `MEZZ`, `T`.':
    'Código del piso dentro del edificio: `S1`, `P0`, `P1`, `MEZZ`, `T`.',
  'Everything needed to draw one floor in a single call.':
    'Todo lo necesario para dibujar un piso en una sola llamada.',
  'Every space on the floor, placed or not, ordered by code.':
    'Todos los espacios del piso, ubicados o no, ordenados por código.',
  'Identifier within the building. **Not for display** - see `doorCode`.':
    'Identificador dentro del edificio. **No es para mostrar**: vea `doorCode`.',
  'Exactly as printed on the door. Absent when the door has no number. The only code a client shows.':
    'Exactamente como está impreso en la puerta. No aparece cuando la puerta no tiene número. Es el único código que muestra un cliente.',
  '`doorCode` without its wing\'s suffix. Absent without a door code.':
    '`doorCode` sin el sufijo de su ala. No aparece si no hay código de puerta.',
  'Code of one of the building\'s wings.':
    'Código de una de las alas del edificio.',
  'The fixed family a space type belongs to. **Clients choose colours and icons by\ncategory**; the types under it are data the portal adds to.\n\n| Value | Spanish | Examples |\n| --- | --- | --- |\n| `TEACHING` | Docencia | aulas, laboratorios, salas de cómputo, auditorios, biblioteca |\n| `PUBLIC_SERVICE` | Atención | ventanillas, recepción, consultorios, enfermería |\n| `OFFICE` | Oficina | direcciones, decanaturas, salas de juntas, archivo |\n| `SOCIAL` | Bienestar y social | cafeterías, gimnasio, salas de juego, terrazas |\n| `FACILITIES` | Servicio | baños, cuartos de aseo y de TI, planta eléctrica, parqueadero |\n| `CIRCULATION` | Circulación | ascensores, escaleras, rampas, entradas |\n| `OTHER` | Sin identificar | drawn on a plan, not yet known |':
    'La familia fija a la que pertenece un tipo de espacio. **Los clientes eligen colores e íconos por categoría**; los tipos que tiene debajo son datos que el portal va agregando.\n\n| Valor | En español | Ejemplos |\n| --- | --- | --- |\n| `TEACHING` | Docencia | aulas, laboratorios, salas de cómputo, auditorios, biblioteca |\n| `PUBLIC_SERVICE` | Atención | ventanillas, recepción, consultorios, enfermería |\n| `OFFICE` | Oficina | direcciones, decanaturas, salas de juntas, archivo |\n| `SOCIAL` | Bienestar y social | cafeterías, gimnasio, salas de juego, terrazas |\n| `FACILITIES` | Servicio | baños, cuartos de aseo y de TI, planta eléctrica, parqueadero |\n| `CIRCULATION` | Circulación | ascensores, escaleras, rampas, entradas |\n| `OTHER` | Sin identificar | dibujado en un plano, todavía no se sabe qué es |',
  'The room\'s walls, corners in order, in the floor\'s units. Absent while the space is inventoried but not drawn.':
    'Los muros del salón, con las esquinas en orden, en las unidades del piso. No aparece mientras el espacio está en el inventario pero no se ha dibujado.',
  'The rectangle around a shape, in the floor\'s units.':
    'El rectángulo alrededor de una forma, en las unidades del piso.',
  'Openings on the room\'s walls. Empty when none is drawn yet.':
    'Las aberturas en los muros del salón. Vacío cuando todavía no se ha dibujado ninguna.',
  'A way into a space: the stretch of its outline the door takes up, jamb to jamb. Both ends\nlie on the space\'s `shape`, along one of its edges.':
    'Una entrada a un espacio: el tramo de su contorno que ocupa la puerta, de jamba a jamba. Ambos extremos quedan sobre el `shape` del espacio, a lo largo de uno de sus lados.',
  'Code of the lift, staircase or entrance that serves this space - "sube por el ascensor central".':
    'Código del ascensor, la escalera o la entrada que sirve a este espacio: "sube por el ascensor central".',
  'How to get there when `accessVia` is not enough.':
    'Cómo llegar cuando `accessVia` no basta.',
  'Save everything drawn on a floor at once':
    'Guardar de una vez todo lo dibujado en un piso',
  'What the floor editor sends after someone walks a floor. `spaces` is the **complete**\nlist: a space of this floor missing from it is deleted, a new one is created, the rest are\nreplaced. **All or nothing** - if any space is wrong, nothing is written and every problem\nis reported against its position (`spaces[3].shape`).\n\n`version` is the floor\'s version as the editor loaded it. When someone saved the floor\nsince, the save is refused with `409` rather than silently replacing their work; reload\nand reapply. A successful save returns the floor with its next version.\n\nAlso `409`: removing a space that rooms on another floor name as their `accessVia`.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'Lo que envía el editor de pisos después de que alguien recorre un piso. `spaces` es la lista **completa**: un espacio de este piso que falte en ella se elimina, uno nuevo se crea, y los demás se reemplazan. **Todo o nada**: si algún espacio está mal, no se escribe nada y cada problema se informa con su posición (`spaces[3].shape`).\n\n`version` es la versión del piso tal como la cargó el editor. Si alguien guardó el piso desde entonces, el guardado se rechaza con `409` en lugar de reemplazar su trabajo en silencio; recargue y vuelva a aplicar. Un guardado exitoso devuelve el piso con su siguiente versión.\n\nTambién `409`: quitar un espacio que salones de otro piso nombran como su `accessVia`.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  'Search or list spaces across the campus':
    'Buscar o listar espacios en todo el campus',
  '**With `q`:** matched against the door code, the name and the aliases, case- and\naccent-insensitively, ordered by descending relevance. It matches whole words, not\nprefixes: `sistemas` finds the lab, `sistem` does not. Internal codes are never matched,\nso a generated code cannot surface through the search.\n\n**Without `q`:** every space the filters allow, ordered by building, floor and code -\nhow a floor is managed rather than how a person finds a room.\n\n`type` takes one type code; `category` takes a whole category. Unplaced spaces are\nincluded.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    '**Con `q`:** coincide con el código de puerta, el nombre y los alias, sin distinguir mayúsculas ni tildes, ordenado por relevancia descendente. Coincide con palabras completas, no con prefijos: `sistemas` encuentra el laboratorio, `sistem` no. Los códigos internos nunca coinciden, así que un código generado no puede aparecer en la búsqueda.\n\n**Sin `q`:** todos los espacios que permiten los filtros, ordenados por edificio, piso y código: así se administra un piso, no así busca una persona un salón.\n\n`type` recibe un código de tipo; `category` recibe una categoría completa. Se incluyen los espacios sin ubicar.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Words to search for. At least 2 characters.':
    'Palabras que se buscan. Al menos 2 caracteres.',
  'A type code from `GET /api/map/space-types`.':
    'Un código de tipo de `GET /api/map/space-types`.',
  'A wing code of the building.':
    'Un código de ala del edificio.',
  'A floor code; meaningful together with `buildingCode`.':
    'Un código de piso; tiene sentido junto con `buildingCode`.',
  'Page size. Capped at 100 - a larger value is a 400, never silently clamped.':
    'Tamaño de página. Máximo 100: un valor mayor es un 400, nunca se recorta en silencio.',
  'A page of spaces. The only paginated collection in this API.':
    'Una página de espacios. La única colección paginada de esta API.',
  'Create a space':
    'Crear un espacio',
  'Adds one space to a floor. Omit `shape` to inventory it without drawing it. A space whose\nshape covers part of another space\'s is refused with `409`; every other mistake - a shape\noff the floor, with no area or crossing itself, a door off its outline - is a `400`.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'Agrega un espacio a un piso. Omita `shape` para inventariarlo sin dibujarlo. Un espacio cuya forma cubre parte de la de otro se rechaza con `409`; cualquier otro error (una forma fuera del piso, sin área o que se cruza consigo misma, una puerta fuera de su contorno) es un `400`.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  'Resolve a code to its space, floor and building':
    'Resolver un código a su espacio, piso y edificio',
  'The endpoint the schedule screen calls when a student taps a class: one round trip from\nthe code to the space, its floor and a summary of its building.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'El endpoint que llama la pantalla del horario cuando un estudiante toca una clase: un solo viaje del código al espacio, su piso y un resumen de su edificio.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'The space\'s `code`. For a numbered room it is the door code, which is also what the KApp\nschedule service stores for a class.':
    'El `code` del espacio. Para un salón numerado es el código de puerta, que también es lo que guarda el servicio de horarios de KApp para una clase.',
  'Needed **only** when the same code exists in more than one building; without it an\nambiguous code is answered with `409` listing the candidates.':
    'Necesario **solo** cuando el mismo código existe en más de un edificio; sin él, un código ambiguo se responde con `409` y la lista de candidatos.',
  'A space, its floor and a summary of its building, in one round trip.':
    'Un espacio, su piso y un resumen de su edificio, en un solo viaje.',
  'A building without its floors.':
    'Un edificio sin sus pisos.',
  'Replace a space':
    'Reemplazar un espacio',
  'Replaces a space. Moving one - to another cell, floor or building - is done here too.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'Reemplaza un espacio. Moverlo (a otra celda, piso o edificio) también se hace aquí.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  'Delete a space':
    'Eliminar un espacio',
  'Refused with `409` while other spaces name it as their `accessVia` - deleting the lift\nthree rooms say to take would leave them pointing at nothing.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'Se rechaza con `409` mientras otros espacios lo nombren como su `accessVia`: eliminar el ascensor que tres salones dicen tomar los dejaría apuntando a nada.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  'List space types':
    'Listar los tipos de espacio',
  'The whole catalogue, grouped by category in the category\'s declared order, then by name.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'El catálogo completo, agrupado por categoría en el orden declarado de las categorías, y luego por nombre.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'A kind of space in the catalogue.':
    'Un tipo de espacio del catálogo.',
  'Create a space type':
    'Crear un tipo de espacio',
  'Adds a kind of space to the catalogue. Usable by spaces immediately.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'Agrega un tipo de espacio al catálogo. Los espacios lo pueden usar de inmediato.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  'Rename a space type or move it to another category':
    'Cambiar el nombre de un tipo de espacio o pasarlo a otra categoría',
  'The code never changes - spaces store it - so the `code` in the body is ignored.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'El código nunca cambia (los espacios lo guardan), así que el `code` del cuerpo se ignora.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  'Delete a space type':
    'Eliminar un tipo de espacio',
  'Refused with `409` while any space uses it.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'Se rechaza con `409` mientras algún espacio lo use.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  'List campuses':
    'Listar las sedes',
  'Every campus that has at least one building, with its building count.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Todas las sedes que tienen al menos un edificio, con su número de edificios.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'A campus (Sede) and how many buildings the map holds for it.':
    'Una sede y cuántos edificios tiene el mapa para ella.',
  'The ground around a campus':
    'El terreno alrededor de una sede',
  'The city around a campus - its blocks, sidewalks, roadways, medians and named streets, and\nthe lots of the blocks the university\'s buildings stand on - from the city\'s reference\nmap, for drawing under the buildings. Coordinates are\n`[lon, lat]` in WGS 84; lay a building\'s drawing on them with its `placement`. The campus\nname matches ignoring case and accents. `404` for a campus with no ground yet.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'La ciudad alrededor de una sede (sus manzanas, andenes, calzadas, separadores y calles con nombre, y los lotes de las manzanas donde están los edificios de la universidad) tomada del mapa de referencia de la ciudad, para dibujar debajo de los edificios. Las coordenadas son `[lon, lat]` en WGS 84; ubique encima el dibujo de un edificio con su `placement`. El nombre de la sede coincide sin distinguir mayúsculas ni tildes. `404` para una sede que todavía no tiene terreno.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'The city around a campus, from the city\'s reference map.':
    'La ciudad alrededor de una sede, tomada del mapa de referencia de la ciudad.',
  'Where the data comes from, to credit wherever it is shown.':
    'De dónde vienen los datos, para dar el crédito dondequiera que se muestren.',
  'The day it was taken from the source.':
    'El día en que se tomaron de la fuente.',
  'The city blocks (manzanas) - where private land meets the sidewalk.':
    'Las manzanas: donde el terreno privado se encuentra con el andén.',
  'The sidewalks (andenes).':
    'Los andenes.',
  'The roadways (calzadas), where cars go.':
    'Las calzadas, por donde van los carros.',
  'The medians (separadores) between roadways.':
    'Los separadores entre calzadas.',
  'The name as the street signs abbreviate it.':
    'El nombre como lo abrevian las placas de la calle.',
  'The street\'s axis.':
    'El eje de la calle.',
  'The cadastral lots of the blocks the university\'s buildings stand on - whose land is whose, for surveying a block.':
    'Los lotes catastrales de las manzanas donde están los edificios de la universidad: de quién es cada terreno, para levantar una manzana.',
  'A cadastral lot.':
    'Un lote catastral.',
  'The cadastre\'s code: the block\'s nine digits, then the lot\'s three.':
    'El código del catastro: los nueve dígitos de la manzana y luego los tres del lote.',
  'What else stands on a campus\'s blocks':
    'Lo demás que hay en las manzanas de una sede',
  'Everything on the campus\'s blocks that is not the university\'s - a neighbour\'s building,\na heritage house with its garden - for drawing around the buildings. An empty list, at\nversion `0`, while nothing has been saved. The campus name matches ignoring case and\naccents.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Todo lo que hay en las manzanas de la sede que no es de la universidad (el edificio de un vecino, una casa patrimonial con su jardín), para dibujarlo alrededor de los edificios. Una lista vacía, en la versión `0`, mientras no se haya guardado nada. El nombre de la sede coincide sin distinguir mayúsculas ni tildes.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'Everything on a campus\'s blocks that is not the university\'s.':
    'Todo lo que hay en las manzanas de una sede que no es de la universidad.',
  'Something on a campus\'s blocks that is not the university\'s - a neighbour\'s building, a\nheritage house with its garden - drawn around the buildings so they read in their place.':
    'Algo en las manzanas de una sede que no es de la universidad (el edificio de un vecino, una casa patrimonial con su jardín), dibujado alrededor de los edificios para que se lean en su lugar.',
  'What it is called, or what it is.':
    'Cómo se llama, o qué es.',
  'How many floors it rises above the street; 0 for ground, like a garden.':
    'Cuántos pisos sube sobre la calle; 0 si está a ras de suelo, como un jardín.',
  'The cadastral lot it stands on, when known.':
    'El lote catastral sobre el que está, cuando se sabe.',
  'Bumped by every save. Send it back with the next one.':
    'Sube con cada guardado. Devuélvalo con el siguiente.',
  'When it was last saved; absent while nothing has been.':
    'Cuándo se guardó por última vez; no aparece mientras no se haya guardado nada.',
  'Replace what else stands on a campus\'s blocks':
    'Reemplazar lo demás que hay en las manzanas de una sede',
  'The **complete** list, as the portal\'s block editor saves it: a structure missing from it\nis gone. `version` is the list\'s version as it was read, `0` while nothing has been saved;\nwhen somebody saved it since, the save is refused with `409` and nothing changes - reload\nand reapply. Every outline must close, `400` otherwise.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'La lista **completa**, como la guarda el editor de manzanas del portal: una estructura que falte en ella desaparece. `version` es la versión de la lista tal como se leyó, `0` mientras no se haya guardado nada; si alguien la guardó desde entonces, el guardado se rechaza con `409` y nada cambia: recargue y vuelva a aplicar. Todo contorno debe cerrar; si no, `400`.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  'The distances taken round a campus\'s blocks':
    'Las distancias tomadas alrededor de las manzanas de una sede',
  'Where each wall stands from the curb and how long it is, as taken on site with the portal\'s\nsurvey sheet. None, at version `0`, while nothing has been saved. The campus name matches\nignoring case and accents.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.':
    'Dónde queda cada muro respecto al sardinel y cuánto mide, tal como se tomó en el sitio con la hoja de levantamiento del portal. Ninguna, en la versión `0`, mientras no se haya guardado nada. El nombre de la sede coincide sin distinguir mayúsculas ni tildes.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_ADMIN`.',
  'The distances taken on site round a campus\'s blocks.':
    'Las distancias tomadas en el sitio alrededor de las manzanas de una sede.',
  'One distance taken on site round a campus\'s blocks, with a tape or a phone\'s Measure app:\nhow far a wall stands from the curb, or how long it is.':
    'Una distancia tomada en el sitio alrededor de las manzanas de una sede, con un metro o con la app Medidas de un teléfono: a qué distancia del sardinel está un muro, o cuánto mide.',
  'Which distance of the portal\'s survey plan it is, or an extra one\'s own id.':
    'Qué distancia del plan de levantamiento del portal es, o el id propio de una adicional.',
  'What an extra distance is of; absent for one the plan names.':
    'De qué es una distancia adicional; no aparece para una que el plan nombra.',
  'What was typed, as it was typed: pieces joined with +.':
    'Lo que se escribió, tal como se escribió: los tramos unidos con +.',
  'The total the portal read from the text; absent while it reads none.':
    'El total que leyó el portal del texto; no aparece mientras no lea ninguno.',
  'What was seen on site that the sketch does not show.':
    'Lo que se vio en el sitio y el croquis no muestra.',
  'True while it is asked to be taken again: what was typed stays, for reference, until it is. Absent otherwise.':
    'Verdadero mientras se pide volver a tomarla: lo que se escribió queda, como referencia, hasta que se tome. No aparece en otro caso.',
  'When it was last changed. Set by the server; ignored in a request.':
    'Cuándo se cambió por última vez. Lo fija el servidor; se ignora en una solicitud.',
  'Save the distances taken round a campus\'s blocks':
    'Guardar las distancias tomadas alrededor de las manzanas de una sede',
  'The **complete** list: a distance missing from it is gone. `version` is the survey\'s\nversion as it was read, `0` while nothing has been saved; when it was saved from somewhere\nelse since, the save is refused with `409` and nothing changes - read it again and lay the\nchanges over it. Two distances with one id, or a negative one, are `400`. `updatedAt` is\nthe server\'s: a distance keeps the time it was taken until its text, total, note or label\nchanges.\n\nAllowed roles: `ROLE_ADMIN` only.':
    'La lista **completa**: una distancia que falte en ella desaparece. `version` es la versión del levantamiento tal como se leyó, `0` mientras no se haya guardado nada; si se guardó desde otra parte desde entonces, el guardado se rechaza con `409` y nada cambia: vuelva a leerlo y ponga los cambios encima. Dos distancias con un mismo id, o una negativa, son un `400`. `updatedAt` es del servidor: una distancia conserva la hora en que se tomó hasta que cambie su texto, su total, su nota o su etiqueta.\n\nRoles permitidos: solo `ROLE_ADMIN`.',
  // auth.openapi.yaml, contracts 2.0 (October 2026)
  'Sign in with Microsoft':
    'Iniciar sesión con Microsoft',
  'Exchanges the ID token Microsoft issued for KApp\'s access and refresh tokens.\n\nPublic endpoint: no KApp token required. The ID token is the proof, and the gateway\nrate-limits this like every credential endpoint.\n\nThe ID token must be signed by the university\'s tenant, issued to KApp\'s application\n(`aud`), and unexpired. The account is created the first time its owner signs in, and its\nnames are refreshed from the token every time.\n\n`client` says which client is signing in. `PORTAL` requires a permission: an account with\nnone is refused with `403` and an issue for the field `client`, and nothing is issued.\n\nOutcomes:\n\n- `200` signed in.\n- `401` the ID token is not valid: wrong signature, wrong tenant or audience, expired. One\n  message for all of them.\n- `403` the account is deactivated, or it has no permission and `client` is `PORTAL`.':
    'Cambia el token de identidad que emitió Microsoft por los tokens de acceso y de actualización de KApp.\n\nEndpoint público: no requiere token de KApp. El token de identidad es la prueba, y el gateway le limita los intentos como a todo endpoint de credenciales.\n\nEl token de identidad debe estar firmado por el tenant de la universidad, emitido para la aplicación de KApp (`aud`) y sin vencer. La cuenta se crea la primera vez que su dueño inicia sesión, y sus nombres se actualizan desde el token cada vez.\n\n`client` dice qué cliente está iniciando sesión. `PORTAL` exige un permiso: una cuenta sin ninguno se rechaza con `403` y un problema para el campo `client`, y no se emite nada.\n\nResultados:\n\n- `200` sesión iniciada.\n- `401` el token de identidad no es válido: firma incorrecta, tenant o audiencia equivocados, vencido. Un solo mensaje para todos.\n- `403` la cuenta está desactivada, o no tiene ningún permiso y `client` es `PORTAL`.',
  'KApp\'s tokens and the minimum context a client needs to render the right navigation\nwithout an extra profile call.':
    'Los tokens de KApp y el contexto mínimo que necesita un cliente para armar la navegación correcta sin una llamada extra al perfil.',
  'Access-token lifetime in seconds from issuance. One hour for an account; 24 hours for\na redeemed visitor pass.':
    'Vida del token de acceso en segundos desde que se emite. Una hora para una cuenta; 24 horas para un pase de visitante canjeado.',
  'Opaque. Renews the session through `POST /auth/refresh`, and is replaced by a new one\nevery time. Keep it where the platform keeps secrets: the Keychain on iOS, encrypted\nstorage on Android. `null` for a visitor pass, which does not renew.':
    'Opaco. Renueva la sesión con `POST /auth/refresh`, y cada vez se reemplaza por uno nuevo. Guárdelo donde la plataforma guarda los secretos: el llavero (Keychain) en iOS, el almacenamiento cifrado en Android. `null` para un pase de visitante, que no se renueva.',
  'Seconds the refresh token lasts if it is not used: 30 days in the apps, 12 hours in\nthe portal. `null` with no refresh token.':
    'Segundos que dura el token de actualización si no se usa: 30 días en las apps, 12 horas en el portal. `null` cuando no hay token de actualización.',
  'The profile role and the permissions of the account, mirroring the `roles` claim.\nClients use these to hide interface they cannot reach, but authorization is always\nre-checked server side.':
    'El rol de perfil y los permisos de la cuenta, iguales al claim `roles`. Los clientes los usan para ocultar la interfaz a la que no pueden llegar, pero la autorización siempre se vuelve a revisar en el servidor.',
  'A role a token may carry. `ROLE_STUDENT`, `ROLE_PROFESSOR` and `ROLE_STAFF` are profile\nroles, exactly one per account. `ROLE_ADMIN`, `ROLE_RECEPTION`, `ROLE_MAINTENANCE`,\n`ROLE_MODERATION` and `ROLE_WELLBEING` are permissions, which add to it. `ROLE_GUEST` is\nonly ever alone, on a visitor\'s token.':
    'Un rol que puede llevar un token. `ROLE_STUDENT`, `ROLE_PROFESSOR` y `ROLE_STAFF` son roles de perfil, exactamente uno por cuenta. `ROLE_ADMIN`, `ROLE_RECEPTION`, `ROLE_MAINTENANCE`, `ROLE_MODERATION` y `ROLE_WELLBEING` son permisos, que se suman a él. `ROLE_GUEST` siempre va solo, en el token de un visitante.',
  'Renew the access token':
    'Renovar el token de acceso',
  'Exchanges a refresh token for a new access token **and a new refresh token**. The one sent\nstops working at once.\n\nPublic endpoint: no access token required, since the point is that it may have expired.\n\nOutcomes:\n\n- `200` renewed. Keep the new refresh token and forget the old one.\n- `401` the refresh token is unknown, expired or revoked. The client signs in again.\n- `401` as well when the refresh token **was already used**: somebody holds a copy, so its\n  whole family is revoked and every device holding one of them has to sign in again.\n- `403` the account was deactivated.\n\nThe roles in the new tokens are the account\'s roles now, so a permission granted or taken\naway reaches the client at its next renewal.':
    'Cambia un token de actualización por un token de acceso nuevo **y un token de actualización nuevo**. El que se envió deja de funcionar en ese momento.\n\nEndpoint público: no requiere token de acceso, porque justamente puede estar vencido.\n\nResultados:\n\n- `200` renovado. Guarde el token de actualización nuevo y olvide el anterior.\n- `401` el token de actualización es desconocido, venció o fue revocado. El cliente vuelve a iniciar sesión.\n- `401` también cuando el token de actualización **ya se había usado**: alguien tiene una copia, así que se revoca toda su familia y cada dispositivo que tenga uno de ellos tiene que volver a iniciar sesión.\n- `403` la cuenta fue desactivada.\n\nLos roles de los tokens nuevos son los que tiene la cuenta ahora, así que un permiso otorgado o retirado le llega al cliente en su siguiente renovación.',
  'Sign out':
    'Cerrar sesión',
  'Revokes the refresh token sent and its whole family, so no device holding one of them can\nrenew. The access token in hand keeps working until it expires, within the hour; the\nclient discards it.\n\nPublic endpoint, and **always `204`**: signing out with a token that is already unknown or\nrevoked is not an error, so a client can always sign out.':
    'Revoca el token de actualización enviado y toda su familia, para que ningún dispositivo que tenga uno de ellos pueda renovar. El token de acceso que se tiene sigue funcionando hasta que vence, dentro de la hora; el cliente lo descarta.\n\nEndpoint público, y **siempre `204`**: cerrar sesión con un token que ya es desconocido o está revocado no es un error, así que un cliente siempre puede cerrar sesión.',
  'Publishes the public half of the RS256 signing keys as a JWKS document.\n\nPublic endpoint: no token required, and it is exposed unauthenticated on\npurpose. Every other KApp service fetches this document, caches it by `kid`, and uses it\nto validate the signature of incoming access tokens. No service holds the private key\nand no shared secret is distributed.\n\nMore than one key may be present during a rotation: the retiring key\nstays published until the last token signed with it has expired. Consumers\nmust select the key whose `kid` matches the token header rather than\nassuming a single entry.':
    'Publica la mitad pública de las claves de firma RS256 como un documento JWKS.\n\nEndpoint público: no requiere token, y se expone sin autenticación a propósito. Todos los demás servicios de KApp obtienen este documento, lo guardan en caché por `kid` y lo usan para validar la firma de los tokens de acceso que reciben. Ningún servicio tiene la clave privada y no se reparte ningún secreto compartido.\n\nDurante una rotación puede haber más de una clave: la que se retira sigue publicada hasta que vence el último token firmado con ella. Los consumidores deben elegir la clave cuyo `kid` coincide con el encabezado del token en lugar de suponer que hay una sola.',
  'Liveness probe used by Docker Compose, Kubernetes and the gateway circuit breaker.\n\nPublic endpoint: no token required. It reports only whether the service\nprocess is answering; it deliberately exposes no build, dependency or\ndatabase detail.':
    'Sonda de vida que usan Docker Compose, Kubernetes y el cortocircuito del gateway.\n\nEndpoint público: no requiere token. Solo informa si el proceso del servicio responde; a propósito no expone detalles de compilación, dependencias ni base de datos.',
  'Set an account\'s profile role and permissions':
    'Fijar el rol de perfil y los permisos de una cuenta',
  'Requires `ROLE_ADMIN`. Replaces the account\'s roles with exactly the ones sent: one\n`profileRole` and any `permissions`. They reach the person\'s tokens at their next renewal,\nwithin the hour.\n\n- `profileRole` overrides what the Microsoft directory says, for an account it does not\n  classify or classifies wrong.\n- `400` for a permission listed twice, or a profile role sent as a permission.\n- `400` as well when an administrator would take `ROLE_ADMIN` away from themselves, so\n  the portal always keeps someone who can manage it.':
    'Requiere `ROLE_ADMIN`. Reemplaza los roles de la cuenta exactamente por los enviados: un `profileRole` y los `permissions` que haya. Llegan a los tokens de la persona en su siguiente renovación, dentro de la hora.\n\n- `profileRole` reemplaza lo que dice el directorio de Microsoft, para una cuenta que no clasifica o que clasifica mal.\n- `400` para un permiso repetido, o un rol de perfil enviado como permiso.\n- `400` también cuando un administrador se quitaría `ROLE_ADMIN` a sí mismo, para que el portal siempre conserve a alguien que pueda administrarlo.',
  'The profile role first, then the permissions.':
    'Primero el rol de perfil, después los permisos.',
  'Requires `ROLE_ADMIN`. Removes the account and its refresh tokens, then asks the User API\nto remove its profile. Deleting again is safe: an account already gone still has its\nprofile removed. Never an administrator\'s account, and never one\'s own: `400`. `503` when\nthe profile could not be removed yet; the account already signs nobody in, and deleting\nagain finishes it.\n\nSigning in with Microsoft again creates a new, empty account.':
    'Requiere `ROLE_ADMIN`. Quita la cuenta y sus tokens de actualización, y luego le pide a la API de usuarios que quite su perfil. Borrar otra vez es seguro: a una cuenta que ya no está igual se le quita el perfil. Nunca la cuenta de un administrador, ni la propia: `400`. `503` cuando todavía no se pudo quitar el perfil; la cuenta ya no deja entrar a nadie, y borrar otra vez lo termina.\n\nVolver a iniciar sesión con Microsoft crea una cuenta nueva, vacía.',
  'Exchanges a pass for a token valid for **24 hours** that opens the campus map and\nnothing else. There is no refresh token: a pass is for today.\n\nPublic, necessarily: a visitor has no account and nothing to authenticate with. The\ncode *is* the credential, which is why it is single-use, stops being redeemable 12\nhours after it was issued, and is rate-limited at the gateway alongside the sign-in.\n\nThe returned token carries `ROLE_GUEST` and no other role. That is not a special\nvisitor permission — it is the role every other service already refuses everywhere\nbut the map, so "map only" is the authorization matrix the services enforce rather\nthan a second mechanism that could drift away from it. The `sub` claim is\n`visitor:<pass id>`, not a user id, and there is no `email` claim: there is no\naccount behind it.\n\n**The identity document is required.** The pass exists so reception has a record of\nwho was in the building; redeemed anonymously it would be an account with extra\nsteps. That record is personal data under Ley 1581 de 2012 and is **deleted\nautomatically 30 days** after redemption.':
    'Cambia un pase por un token válido por **24 horas** que abre el mapa del campus y nada más. No hay token de actualización: un pase es para hoy.\n\nEs público por necesidad: un visitante no tiene cuenta ni nada con qué autenticarse. El código *es* la credencial; por eso es de un solo uso, deja de poder canjearse 12 horas después de emitido y tiene límite de solicitudes en el gateway junto con el inicio de sesión.\n\nEl token devuelto lleva `ROLE_GUEST` y ningún otro rol. No es un permiso especial para visitantes: es el rol que todos los demás servicios ya rechazan en todas partes salvo en el mapa, así que "solo el mapa" es la matriz de autorización que los servicios aplican y no un segundo mecanismo que podría desviarse de ella. El claim `sub` es `visitor:<pass id>`, no un id de usuario, y no hay claim `email`: no hay una cuenta detrás.\n\n**El documento de identidad es obligatorio.** El pase existe para que recepción tenga un registro de quién estuvo en el edificio; canjeado de forma anónima sería una cuenta con pasos de más. Ese registro es un dato personal según la Ley 1581 de 2012 y se **elimina automáticamente a los 30 días** del canje.',
  'Requires `ROLE_RECEPTION` or `ROLE_ADMIN`. Newest first.\n\n**This returns personal data**: visitors\' names and identity documents. That is the\npoint of a register, and the reason it exists nowhere else in the API. Records are\ndeleted automatically 30 days after redemption.':
    'Requiere `ROLE_RECEPTION` o `ROLE_ADMIN`. Primero los más recientes.\n\n**Esto devuelve datos personales**: los nombres y los documentos de identidad de los visitantes. Ese es el sentido de un registro, y la razón por la que no existe en ninguna otra parte de la API. Los registros se eliminan automáticamente a los 30 días del canje.',
  'A pass as reception sees it, including who redeemed it. Returned only to\n`ROLE_RECEPTION` and `ROLE_ADMIN`, and only from `/auth/admin/**`.':
    'Un pase como lo ve recepción, incluido quién lo canjeó. Solo se devuelve a `ROLE_RECEPTION` y `ROLE_ADMIN`, y solo desde `/auth/admin/**`.',
  'The person who issued it, so a pass traces back to somebody.':
    'La persona que lo emitió, para que un pase lleve hasta alguien.',
  'Requires `ROLE_RECEPTION` or `ROLE_ADMIN`. Returns a code to read out to the visitor.\n\nThe code avoids `I`, `O`, `0` and `1`: it is read aloud across a counter and typed by\nsomebody who has never seen it written down.\n\nRedeemable for 12 hours. Once redeemed the visitor has 24 hours of map-only access.':
    'Requiere `ROLE_RECEPTION` o `ROLE_ADMIN`. Devuelve un código para leerle en voz alta al visitante.\n\nEl código evita `I`, `O`, `0` y `1`: se lee en voz alta sobre un mostrador y lo escribe alguien que nunca lo ha visto escrito.\n\nSe puede canjear durante 12 horas. Una vez canjeado, el visitante tiene 24 horas de acceso solo al mapa.',
  'Requires `ROLE_RECEPTION` or `ROLE_ADMIN`.\n\n`409` for a pass that has already been redeemed. That row is no longer a pass, it is\nthe record of a visit, and a register reception could quietly edit is not a register.\nIt leaves on its own after 30 days.':
    'Requiere `ROLE_RECEPTION` o `ROLE_ADMIN`.\n\n`409` para un pase que ya se canjeó. Esa fila ya no es un pase, es el registro de una visita, y un registro que recepción pudiera editar en silencio no es un registro. Se va sola a los 30 días.',
  'Sign in with a password (development only)':
    'Iniciar sesión con contraseña (solo desarrollo)',
  '**Development only**: `404` unless local sign-in is enabled. Verifies an e-mail and\npassword pair and answers exactly as `POST /auth/microsoft` does, refresh token included.\n\nOutcomes:\n\n- `200` credentials valid and the address is verified.\n- `401` unknown e-mail or wrong password. The message is intentionally\n  identical for both so the response does not reveal which accounts\n  exist.\n- `403` credentials are valid but the address has not been confirmed\n  yet. Clients should route the user to the "resend verification"\n  screen.\n- `403` with an issue for the field `newPassword` in `details`: the\n  password is a temporary one an administrator issued. Clients ask for a\n  new password and send both to `POST /auth/password`.\n- `401` as well for a temporary password past its expiry: an\n  administrator has to issue a new one.\n- `403` with an issue for the field `allowedRoles`: the credentials are\n  valid, and the account has none of the roles the client named. Checked\n  before the temporary password.':
    '**Solo para desarrollo**: `404` salvo que el inicio de sesión local esté habilitado. Verifica un par de correo y contraseña y responde exactamente como `POST /auth/microsoft`, con token de actualización incluido.\n\nResultados:\n\n- `200` las credenciales son válidas y la dirección está verificada.\n- `401` correo desconocido o contraseña incorrecta. El mensaje es idéntico en ambos casos a propósito, para que la respuesta no revele qué cuentas existen.\n- `403` las credenciales son válidas pero la dirección aún no se ha confirmado. Los clientes deben llevar al usuario a la pantalla de "reenviar verificación".\n- `403` con un problema para el campo `newPassword` en `details`: la contraseña es temporal, la emitió un administrador. Los clientes piden una contraseña nueva y envían las dos a `POST /auth/password`.\n- `401` también para una contraseña temporal vencida: un administrador tiene que emitir otra.\n- `403` con un problema para el campo `allowedRoles`: las credenciales son válidas y la cuenta no tiene ninguno de los roles que nombró el cliente. Se revisa antes que la contraseña temporal.',
  'Replace a password, and sign in with the new one (development only)':
    'Reemplazar una contraseña e iniciar sesión con la nueva (solo desarrollo)',
  '**Development only**: `404` unless local sign-in is enabled. Sets a new password for an\naccount, given the current one, and answers exactly as `POST /auth/login` does. It is how a\ntemporary password an administrator issued is replaced at first sign-in, and it works\nfor any password.\n\nOutcomes:\n\n- `200` the new password is set and the token is for it.\n- `400` the new password is under 10 characters, over 72, or the same as the\n  current one.\n- `401` unknown e-mail, wrong current password, or a temporary password past\n  its expiry. One message for all of them, as with login.\n- `403` the address has not been confirmed yet, or, with an issue for\n  `allowedRoles`, the account has none of the roles the client named. In\n  both cases nothing is changed.':
    '**Solo para desarrollo**: `404` salvo que el inicio de sesión local esté habilitado. Pone una contraseña nueva a una cuenta, dada la actual, y responde exactamente como `POST /auth/login`. Así se reemplaza en el primer inicio de sesión la contraseña temporal que emitió un administrador, y sirve para cualquier contraseña.\n\nResultados:\n\n- `200` la contraseña nueva quedó puesta y el token es para ella.\n- `400` la contraseña nueva tiene menos de 10 caracteres, más de 72, o es igual a la actual.\n- `401` correo desconocido, contraseña actual incorrecta o una contraseña temporal vencida. Un solo mensaje para todos, como en el login.\n- `403` la dirección aún no se ha confirmado o, con un problema para `allowedRoles`, la cuenta no tiene ninguno de los roles que nombró el cliente. En ambos casos no se cambia nada.',
  'Register an account with a password (development only)':
    'Registrar una cuenta con contraseña (solo desarrollo)',
  '**Development only**: `404` unless local sign-in is enabled. Creates an account with a\npassword and asks the User API to create the matching profile.\n\n- `email` must end in one of the configured domains, or it is rejected with `400`.\n- `invitationCode` is mandatory and decides the profile role. No code grants a\n  permission. An unknown, spent or expired code is rejected with `400`.\n- The account is created with `emailVerified: false` and a verification e-mail is sent\n  where e-mail is configured. Sign-in is refused until the address is confirmed.':
    '**Solo para desarrollo**: `404` salvo que el inicio de sesión local esté habilitado. Crea una cuenta con contraseña y le pide a la API de usuarios que cree el perfil correspondiente.\n\n- `email` debe terminar en uno de los dominios configurados, o se rechaza con `400`.\n- `invitationCode` es obligatorio y decide el rol de perfil. Ningún código otorga un permiso. Un código desconocido, agotado o vencido se rechaza con `400`.\n- La cuenta se crea con `emailVerified: false` y se envía un correo de verificación donde el correo está configurado. El inicio de sesión se rechaza hasta que la dirección se confirma.',
  'Confirm an e-mail address (development only)':
    'Confirmar una dirección de correo (solo desarrollo)',
  '**Development only**: `404` unless local sign-in is enabled. Consumes the single-use token\ndelivered in the verification e-mail and marks the address as confirmed. After a\nsuccessful call the account can sign in with its password.\n\nThe token is single use: replaying it returns `400`, same as an expired\nor malformed one. Clients should treat `400` as "ask the user to request\na new link" and call `POST /auth/verify/resend`.':
    '**Solo para desarrollo**: `404` salvo que el inicio de sesión local esté habilitado. Consume el token de un solo uso que llega en el correo de verificación y marca la dirección como confirmada. Después de una llamada exitosa la cuenta puede iniciar sesión con su contraseña.\n\nEl token es de un solo uso: repetirlo devuelve `400`, igual que uno vencido o mal formado. Los clientes deben tratar el `400` como "pídale al usuario que solicite un enlace nuevo" y llamar a `POST /auth/verify/resend`.',
  'Request a new verification e-mail (development only)':
    'Pedir un correo de verificación nuevo (solo desarrollo)',
  '**Development only**: `404` unless local sign-in is enabled. Issues a fresh verification\ntoken and sends it to the address supplied.\n\nThis endpoint **always answers `202`**, whether or not an account with\nthat address exists and whether or not it was already verified. That is\ndeliberate: a per-address distinction would turn this into an account\nenumeration oracle. Clients must show the same neutral confirmation in\nevery case and must never infer account existence from the response.\n\nAny previously issued verification token for the account is invalidated.':
    '**Solo para desarrollo**: `404` salvo que el inicio de sesión local esté habilitado. Emite un token de verificación nuevo y lo envía a la dirección indicada.\n\nEste endpoint **siempre responde `202`**, exista o no una cuenta con esa dirección y esté o no ya verificada. Es deliberado: distinguir por dirección convertiría esto en un oráculo para enumerar cuentas. Los clientes deben mostrar la misma confirmación neutra en todos los casos y nunca deducir de la respuesta si una cuenta existe.\n\nCualquier token de verificación emitido antes para la cuenta queda invalidado.',
  'List invitation codes (development only)':
    'Listar códigos de invitación (solo desarrollo)',
  '**Development only**: `404` unless local sign-in is enabled. Requires `ROLE_ADMIN`.\nInvitation codes gate password registration, so being able to see which exist, how many\nuses each has left and which are still active is the difference between managing access\nand guessing at it.':
    '**Solo para desarrollo**: `404` salvo que el inicio de sesión local esté habilitado. Requiere `ROLE_ADMIN`. Los códigos de invitación controlan el registro con contraseña, así que poder ver cuáles existen, cuántos usos le quedan a cada uno y cuáles siguen activos es la diferencia entre administrar el acceso y adivinarlo.',
  'A code that permits one password registration, for a fixed profile role. Development only.':
    'Un código que permite un registro con contraseña, para un rol de perfil fijo. Solo para desarrollo.',
  'The role that decides what the app shows. Exactly one per account.':
    'El rol que decide qué muestra la app. Exactamente uno por cuenta.',
  'Create an invitation code (development only)':
    'Crear un código de invitación (solo desarrollo)',
  '**Development only**: `404` unless local sign-in is enabled. Requires `ROLE_ADMIN`. The\nprofile role a code grants is fixed at creation. No code grants a permission: an\nadministrator is promoted deliberately, or anyone holding a code could escalate.':
    '**Solo para desarrollo**: `404` salvo que el inicio de sesión local esté habilitado. Requiere `ROLE_ADMIN`. El rol de perfil que otorga un código se fija al crearlo. Ningún código otorga un permiso: a un administrador se le asciende a propósito, o cualquiera con un código podría escalar.',
  'Activate or deactivate an invitation code (development only)':
    'Activar o desactivar un código de invitación (solo desarrollo)',
  '**Development only**: `404` unless local sign-in is enabled. Requires `ROLE_ADMIN`.\nDeactivating is the reversible way to stop a code being redeemed, and it keeps the record\nof who used it.':
    '**Solo para desarrollo**: `404` salvo que el inicio de sesión local esté habilitado. Requiere `ROLE_ADMIN`. Desactivar es la forma reversible de impedir que se canjee un código, y conserva el registro de quién lo usó.',
  'Delete an invitation code (development only)':
    'Borrar un código de invitación (solo desarrollo)',
  '**Development only**: `404` unless local sign-in is enabled. Requires `ROLE_ADMIN`.\nPrefer deactivating: deleting a code that has already been redeemed discards the only\nrecord of how those accounts were created.':
    '**Solo para desarrollo**: `404` salvo que el inicio de sesión local esté habilitado. Requiere `ROLE_ADMIN`. Es mejor desactivar: borrar un código que ya se canjeó descarta el único registro de cómo se crearon esas cuentas.',
  'Create an account with a temporary password (development only)':
    'Crear una cuenta con contraseña temporal (solo desarrollo)',
  '**Development only**: `404` unless local sign-in is enabled. Requires `ROLE_ADMIN`.\nCreates the profile in the User API and the account here, with a temporary password\nreturned once in the response: hand it to the person, who replaces it at first sign-in\n(`POST /auth/password`). It is valid for seven days.\n\n- `profileRole` is the account\'s profile role. No permission is granted this way.\n- `email` must use one of the configured domains, as in registration.\n- The account may sign in at once: an administrator vouched for it. Its\n  address is still recorded as not verified.':
    '**Solo para desarrollo**: `404` salvo que el inicio de sesión local esté habilitado. Requiere `ROLE_ADMIN`. Crea el perfil en la API de usuarios y la cuenta aquí, con una contraseña temporal que se devuelve una sola vez en la respuesta: entréguesela a la persona, que la reemplaza en su primer inicio de sesión (`POST /auth/password`). Es válida por siete días.\n\n- `profileRole` es el rol de perfil de la cuenta. Así no se otorga ningún permiso.\n- `email` debe usar uno de los dominios configurados, como en el registro.\n- La cuenta puede iniciar sesión de inmediato: un administrador respondió por ella. Su dirección igual queda registrada como no verificada.',
  'The account\'s roles.':
    'Los roles de la cuenta.',
  'Give an account a new temporary password (development only)':
    'Darle a una cuenta una contraseña temporal nueva (solo desarrollo)',
  '**Development only**: `404` unless local sign-in is enabled. Requires `ROLE_ADMIN`. For a\nperson who forgot their password: the one they had stops working at once, and the new\ntemporary password, returned once here, must be replaced at their next sign-in. Valid for\nseven days.':
    '**Solo para desarrollo**: `404` salvo que el inicio de sesión local esté habilitado. Requiere `ROLE_ADMIN`. Para alguien que olvidó su contraseña: la que tenía deja de funcionar en ese momento, y la contraseña temporal nueva, que se devuelve una sola vez aquí, debe reemplazarse en su siguiente inicio de sesión. Válida por siete días.',
  'Service-to-service endpoint. The User API calls it when an administrator\ndeactivates or reactivates an account, because whether somebody may sign\nin is decided here while the profile flag lives there.\n\nSuspending an account also **revokes all of its refresh tokens**, so its apps are signed\nout at their next renewal, within the hour an access token lasts.\n\n**Not reachable from outside.** The API gateway does not route\n`/internal/**`; the path exists only on the auth-service port inside the\nDocker network. Do not build any client against it — no web, Kotlin or\nSwift client should ever call it.\n\n**Access: `X-Internal-Token` header only.** No bearer token is involved\nand no user role applies. A missing or wrong token gives `401`.\n\nIdempotent: setting the state the account already holds answers `204` and writes nothing.':
    'Endpoint entre servicios. La API de usuarios lo llama cuando un administrador desactiva o reactiva una cuenta, porque si alguien puede iniciar sesión se decide aquí, mientras que el indicador del perfil vive allá.\n\nSuspender una cuenta también **revoca todos sus tokens de actualización**, así que sus apps se cierran en su siguiente renovación, dentro de la hora que dura un token de acceso.\n\n**No se puede llegar desde afuera.** El gateway de la API no enruta `/internal/**`; la ruta solo existe en el puerto del auth-service dentro de la red de Docker. No construya ningún cliente contra ella: ningún cliente web, Kotlin ni Swift debe llamarla.\n\n**Acceso: solo con el encabezado `X-Internal-Token`.** No interviene ningún token bearer y no aplica ningún rol de usuario. Un token ausente o incorrecto da `401`.\n\nIdempotente: fijar el estado que la cuenta ya tiene responde `204` y no escribe nada.',
  // user.openapi.yaml, contracts 2.0 (October 2026)
  'Returns the profile of the account the bearer token belongs to,\nresolved from the token\'s `sub` claim. No user id is accepted in the\nrequest, so one account can never read another through this path.\n\nFor a student, `academic` carries their program, pensum and level as SINU has them,\nread for this call. It is `null` for everybody else.\n\n`ROLE_GUEST` is refused with `403`: a visitor holds a day pass, not an account.':
    'Devuelve el perfil de la cuenta a la que pertenece el token bearer, resuelto desde el claim `sub` del token. La solicitud no acepta ningún id de usuario, así que una cuenta nunca puede leer otra por esta ruta.\n\nPara un estudiante, `academic` trae su programa, su pensum y su nivel tal como los tiene SINU, leídos para esta llamada. Es `null` para todos los demás.\n\n`ROLE_GUEST` se rechaza con `403`: un visitante tiene un pase de un día, no una cuenta.',
  'A person\'s own profile: their directory entry, plus the academic block when they are a\nstudent. Contains no credential data.':
    'El perfil propio de una persona: su entrada del directorio, más el bloque académico cuando es estudiante. No contiene datos de credenciales.',
  'An account as the directory shows it: who it is, what it may do, and whether it is\nactive. Nothing academic, nothing private.':
    'Una cuenta como la muestra el directorio: quién es, qué puede hacer y si está activa. Nada académico, nada privado.',
  'The institutional address the account signs in with. Read-only here;\nit is owned by the Auth API.':
    'La dirección institucional con la que la cuenta inicia sesión. Aquí es de solo lectura; la administra la API de autenticación.',
  'As Microsoft gives it at sign-in.':
    'Tal como lo entrega Microsoft al iniciar sesión.',
  'The account\'s profile role and its permissions, as the Auth API grants them. Read-only\nhere.':
    'El rol de perfil de la cuenta y sus permisos, tal como los otorga la API de autenticación. Aquí es de solo lectura.',
  'A role an account holds. `ROLE_STUDENT`, `ROLE_PROFESSOR` and `ROLE_STAFF` are profile\nroles, exactly one per account. The rest are permissions, which add to it.':
    'Un rol que tiene una cuenta. `ROLE_STUDENT`, `ROLE_PROFESSOR` y `ROLE_STAFF` son roles de perfil, exactamente uno por cuenta. Los demás son permisos, que se suman a él.',
  'The student\'s program, pensum and level, read from SINU. `null` for anybody who\nis not a student.':
    'El programa, el pensum y el nivel del estudiante, leídos de SINU. `null` para quien no es estudiante.',
  'A student\'s program, pensum and level, read from SINU for the student themselves. Never\nstored by KApp and never shown to anybody else.':
    'El programa, el pensum y el nivel de un estudiante, leídos de SINU para el propio estudiante. KApp nunca los guarda y nunca se los muestra a nadie más.',
  'Semester the student is currently in.':
    'Semestre que cursa el estudiante en este momento.',
  'Changes the caller\'s own avatar. The target is always the token\'s `sub`, so no one can edit\nanother account here.\n\n**Nothing else is editable.** Names come from Microsoft at every sign-in, `email` is owned\nby the Auth API, roles and `active` are administrative, and `academic` is SINU\'s. Sending\nany of them is rejected with `400` rather than being silently dropped, so a client that\nround-trips a full profile object gets a clear error instead of a surprise.':
    'Cambia el avatar de quien llama. El destino siempre es el `sub` del token, así que nadie puede editar otra cuenta por aquí.\n\n**Nada más se puede editar.** Los nombres llegan de Microsoft en cada inicio de sesión, `email` lo administra la API de autenticación, los roles y `active` son administrativos, y `academic` es de SINU. Enviar cualquiera de ellos se rechaza con `400` en lugar de descartarse en silencio, así que un cliente que reenvía un perfil completo recibe un error claro en vez de una sorpresa.',
  'Returns a page of the directory, newest account first.\n\n**Access: `ROLE_ADMIN` only.** Any other authenticated role receives\n`403`. This is the only endpoint that lists accounts other than the\ncaller\'s own, which is why it is restricted.\n\nThe directory carries names, e-mail, roles and activation state, and **nothing\nacademic**: an administrator manages accounts, not records.\n\nFilters combine with AND: `role`, `active` and `q` narrow the same\nresult set. Omitting all three lists every account.':
    'Devuelve una página del directorio, primero las cuentas más recientes.\n\n**Acceso: solo `ROLE_ADMIN`.** Cualquier otro rol autenticado recibe `403`. Es el único endpoint que lista cuentas distintas a la de quien llama, y por eso está restringido.\n\nEl directorio trae nombres, correo, roles y estado de activación, y **nada académico**: un administrador gestiona cuentas, no historias académicas.\n\nLos filtros se combinan con Y: `role`, `active` y `q` acotan el mismo conjunto de resultados. Sin ninguno de los tres se listan todas las cuentas.',
  'Return only accounts holding this role, among others.':
    'Devuelve solo las cuentas que tienen este rol, entre otros.',
  'One page of the directory. Page indexes are zero-based, so `page: 0` is\nthe first page and `first` is true there.':
    'Una página del directorio. Los índices de página empiezan en cero, así que `page: 0` es la primera página y ahí `first` es verdadero.',
  'Entries in this page. Empty when the filters match nothing.':
    'Las entradas de esta página. Vacía cuando los filtros no coinciden con nada.',
  'Returns one entry of the directory by account id: names, e-mail, roles and activation\nstate, with nothing academic.\n\n**Access: `ROLE_ADMIN` only.** Other roles receive `403` even when the\nid is their own; they read their profile through `GET /api/users/me`.\n\nReturns `404` when no account carries that id. A well-formed but unknown\nUUID is a `404`, not a `400`.':
    'Devuelve una entrada del directorio por id de cuenta: nombres, correo, roles y estado de activación, sin nada académico.\n\n**Acceso: solo `ROLE_ADMIN`.** Los demás roles reciben `403` aunque el id sea el suyo; su perfil lo leen con `GET /api/users/me`.\n\nDevuelve `404` cuando ninguna cuenta tiene ese id. Un UUID bien formado pero desconocido es un `404`, no un `400`.',
  'Flips the activation flag of an account. Deactivation is a logical\ndelete: the profile is kept for audit, and the account can no longer\nsign in.\n\n**This writes to two services.** The profile here stops being listed as\nactive, and the User API calls\n`PATCH /internal/credentials/{userId}/status` on the Auth API to suspend\nthe account — because whether somebody may sign in is decided there,\nnot here. The account is changed there first, so a failure part-way leaves\nit locked out rather than listed as inactive while its owner can still\nsign in. If the Auth API cannot be reached, this call fails and nothing\nchanges.\n\nSuspending an account also revokes its refresh tokens, so the app is signed out at its\nnext renewal, within the hour an access token lasts.\n\n**Access: `ROLE_ADMIN` only.** Other roles receive `403`.\n\nThe call is idempotent: setting `active` to the value it already holds\nsucceeds and returns the unchanged entry, writing nothing on either\nside. Returns `404` when no account carries that id.':
    'Cambia el indicador de activación de una cuenta. Desactivar es un borrado lógico: el perfil se conserva para auditoría y la cuenta ya no puede iniciar sesión.\n\n**Esto escribe en dos servicios.** El perfil de aquí deja de aparecer como activo, y la API de usuarios llama a `PATCH /internal/credentials/{userId}/status` en la API de autenticación para suspender la cuenta, porque si alguien puede iniciar sesión se decide allá, no aquí. La cuenta se cambia allá primero, así que una falla a mitad de camino la deja bloqueada en lugar de marcada como inactiva mientras su dueño todavía puede entrar. Si no se puede llegar a la API de autenticación, esta llamada falla y nada cambia.\n\nSuspender una cuenta también revoca sus refresh tokens, así que la app se cierra en su siguiente renovación, dentro de la hora que dura un token de acceso.\n\n**Acceso: solo `ROLE_ADMIN`.** Los demás roles reciben `403`.\n\nLa llamada es idempotente: fijar `active` en el valor que ya tiene funciona y devuelve la entrada sin cambios, sin escribir nada en ninguno de los dos lados. Devuelve `404` cuando ninguna cuenta tiene ese id.',
  'Service-to-service endpoint. The Auth API calls it at every sign-in with Microsoft, and\nwhen it creates or changes an account, so the profile carries the names Microsoft gives\nand the roles the Auth API grants.\n\n**Not reachable from outside.** The API gateway does not route\n`/internal/**`; the path exists only on the user-service port inside the\nDocker network. A request arriving at the gateway for this path gets a\n`404` from the gateway itself and never reaches this service. Do not\nbuild any client against it — no web, Kotlin or Swift client should ever\ncall it.\n\n**Access: `X-Internal-Token` header only.** No bearer token is involved\nand no user role applies. The token is a shared secret injected as an\nenvironment variable in both services. A missing or wrong token gives\n`401`.\n\n**Idempotent upsert keyed by e-mail.** A first call creates the profile;\na repeated call with the same e-mail updates the existing one and\nreturns it. Both return `200`, never `201`, and there is no `409`, so a\nretry after a network timeout is always safe.\n\nThe `id` in the response is the profile identifier the Auth API stores\nand puts in the `sub` claim of every token it signs for this account.':
    'Endpoint entre servicios. La API de autenticación lo llama en cada inicio de sesión con Microsoft, y cuando crea o cambia una cuenta, para que el perfil lleve los nombres que entrega Microsoft y los roles que otorga la API de autenticación.\n\n**No se puede llegar desde afuera.** El gateway de la API no enruta `/internal/**`; la ruta solo existe en el puerto del user-service dentro de la red de Docker. Una solicitud que llega al gateway por esta ruta recibe un `404` del propio gateway y nunca llega a este servicio. No construya ningún cliente contra ella: ningún cliente web, Kotlin ni Swift debe llamarla.\n\n**Acceso: solo con el encabezado `X-Internal-Token`.** No interviene ningún token bearer y no aplica ningún rol de usuario. El token es un secreto compartido que se inyecta como variable de entorno en ambos servicios. Un token ausente o incorrecto da `401`.\n\n**Upsert idempotente por correo.** Una primera llamada crea el perfil; otra con el mismo correo actualiza el existente y lo devuelve. Ambas responden `200`, nunca `201`, y no hay `409`, así que reintentar después de un timeout de red siempre es seguro.\n\nEl `id` de la respuesta es el identificador de perfil que guarda la API de autenticación y que pone en el claim `sub` de todo token que firma para esta cuenta.',
  'Service-to-service endpoint. The Auth API calls it when an administrator\ndeletes an account, after removing the account\'s credential. Not reachable\nfrom outside, with `X-Internal-Token` only, as the upsert above.\n\n**Idempotent.** `204` whether or not the profile existed, so a deletion\nthat timed out can be sent again.':
    'Endpoint entre servicios. La API de autenticación lo llama cuando un administrador borra una cuenta, después de quitar su credencial. No se puede llegar desde afuera, y solo con `X-Internal-Token`, como el upsert de arriba.\n\n**Idempotente.** `204` exista o no el perfil, así que un borrado que se quedó sin respuesta se puede enviar otra vez.',
  // semaphore.openapi.yaml, contracts 2.0 (October 2026)
  'Academic level of the program.':
    'Nivel académico del programa.',
  'Code of the program\'s currently `ACTIVE` pensum, or `null` if it has none.':
    'Código del pensum `ACTIVE` del programa en este momento, o `null` si no tiene ninguno.',
  'Returns the whole pensum document: header, knowledge areas, and **every** pensum item\n(51 for the Ingeniería de Sistemas plan), including its elective slots. This is the single\ncall a client needs to render the semáforo grid.\n\nThe example below is a representative slice of the real plan, not the complete list.':
    'Devuelve el documento completo del pensum: encabezado, áreas de conocimiento y **todos** los ítems del pensum (51 en el plan de Ingeniería de Sistemas), incluidos sus cupos de electiva. Es la única llamada que necesita un cliente para dibujar la grilla del semáforo.\n\nEl ejemplo de abajo es una muestra representativa del plan real, no la lista completa.',
  'Lifecycle state of the plan. `ACTIVE` is the one a program\'s new students follow.':
    'Estado del plan en su ciclo de vida. `ACTIVE` es el que siguen los estudiantes nuevos de un programa.',
  'A knowledge area — one row of the semáforo grid. Area codes are defined per pensum;\nthe Ingeniería de Sistemas plan uses `CB`, `BIS`, `ISA` and `SI`.':
    'Un área de conocimiento: una fila de la grilla del semáforo. Los códigos de área se definen por pensum; el plan de Ingeniería de Sistemas usa `CB`, `BIS`, `ISA` y `SI`.',
  'Every item of the plan, fixed courses and elective slots alike. 51 items for the\nIngeniería de Sistemas plan.':
    'Todos los ítems del plan, materias fijas y cupos de electiva por igual. 51 ítems en el plan de Ingeniería de Sistemas.',
  'One item of a pensum — a fixed course, or an elective slot with no fixed content.\n\n**`pensumItemCode` addresses it, `sinuCode` is what you display.** See the conventions\nabove.':
    'Un ítem de un pensum: una materia fija, o un cupo de electiva sin contenido fijo.\n\n**`pensumItemCode` lo identifica, `sinuCode` es lo que se muestra.** Vea las convenciones de arriba.',
  'KApp\'s stable identifier of this item **within the pensum**, for fixed courses and\nelective slots alike. Path parameters, prerequisites and placements use it. Never\nshown to a person.':
    'El identificador estable que KApp le da a este ítem **dentro del pensum**, para materias fijas y cupos de electiva por igual. Lo usan los parámetros de ruta, los prerrequisitos y las ubicaciones del plan. Nunca se le muestra a una persona.',
  'The course code as the university\'s own system (SINU) carries it, or `null` where it\nis not known yet. **The only code a client shows**; where it is `null`, show the\nname.':
    'El código de la materia tal como lo tiene el sistema de la propia universidad (SINU), o `null` donde todavía no se conoce. **El único código que muestra un cliente**; donde es `null`, se muestra el nombre.',
  'Contact hours across the full 16-week semester: `weeklyHours * 16`.':
    'Horas de contacto en todo el semestre de 16 semanas: `weeklyHours * 16`.',
  '`true` when this item is a slot filled by a course of the elective bank, rather than a\nfixed course.':
    '`true` cuando este ítem es un cupo que se llena con una materia del banco de electivas, en lugar de una materia fija.',
  'The `pensumItemCode`s of the items that must all be `PASSED` before this one can be\ntaken. Empty when it has none.':
    'Los `pensumItemCode` de los ítems que deben estar todos `PASSED` antes de poder tomar este. Vacío cuando no tiene ninguno.',
  'Returns the pensum items of one pensum, optionally narrowed by level, knowledge area,\nor elective-slot flag. Filters combine with AND. Omitting every filter returns the same\nlist as the `courses` array of `GET /api/catalog/pensums/{pensumCode}`.\n\nUseful for rendering one column of the grid (`?level=8`) or one row (`?area=ISA`), and for\nlisting a pensum\'s elective slots (`?isElectiveSlot=true`).':
    'Devuelve los ítems de un pensum, acotados opcionalmente por nivel, área de conocimiento o indicador de cupo de electiva. Los filtros se combinan con Y. Sin ningún filtro devuelve la misma lista que el arreglo `courses` de `GET /api/catalog/pensums/{pensumCode}`.\n\nSirve para dibujar una columna de la grilla (`?level=8`) o una fila (`?area=ISA`), y para listar los cupos de electiva de un pensum (`?isElectiveSlot=true`).',
  'List a semester\'s elective bank':
    'Listar el banco de electivas de un semestre',
  'The courses SINU offers in one period to fill the elective slots of this pensum. The bank\nchanges every semester, which is why it is read for a period and never stored.\n\nA plan uses it to say which course the student means to take in a slot\n(`electiveSinuCode` on a placement). An offering fills any elective slot of the pensum\nunless `slots` names the ones it fills.\n\n`404` when the pensum does not exist. A period SINU has not published yet answers an\nempty list, not an error.':
    'Las materias que SINU ofrece en un periodo para llenar los cupos de electiva de este pensum. El banco cambia cada semestre, y por eso se lee para un periodo y nunca se guarda.\n\nUn plan lo usa para decir qué materia piensa tomar el estudiante en un cupo (`electiveSinuCode` en una ubicación). Una oferta llena cualquier cupo de electiva del pensum, salvo que `slots` nombre los que llena.\n\n`404` cuando el pensum no existe. Un periodo que SINU todavía no ha publicado responde una lista vacía, no un error.',
  'The academic period, `^\\d{4}[12]$`. The current one when omitted.':
    'El periodo académico, `^\\d{4}[12]$`. El actual cuando se omite.',
  'A course SINU offers in one period to fill elective slots.':
    'Una materia que SINU ofrece en un periodo para llenar cupos de electiva.',
  'The course code, as SINU carries it. This is what a placement\'s `electiveSinuCode` names.':
    'El código de la materia, tal como lo tiene SINU. Es lo que nombra el `electiveSinuCode` de una ubicación.',
  'The `pensumItemCode`s of the slots this course fills. Empty means any elective slot of\nthe pensum.':
    'Los `pensumItemCode` de los cupos que llena esta materia. Vacío quiere decir cualquier cupo de electiva del pensum.',
  'Load the catalog backup from a CSV':
    'Cargar el respaldo del catálogo desde un CSV',
  'Requires `ROLE_ADMIN`. Loads whole pensums from a spreadsheet: the backup of the catalog,\nfor when SINU cannot be reached. It is the only write the catalog takes; programs and\npensums are otherwise SINU\'s.\n\nOne row per pensum item. The columns before `pensumItemCode` describe the pensum and are\nrepeated on every one of its rows; the import checks those repeated values agree with\neach other rather than letting the first row silently win. One file may carry several\npensums.\n\n**Nothing is written unless the whole file validates.** A partial import would leave the\ncatalog in a state nobody chose, so every problem is collected first and reported\nagainst the line number of the file.\n\n**Declared totals are checked, not trusted.** The import adds up the courses and compares\nagainst `declaredCredits` and `declaredHours` — the latter being the sum of *weekly*\nhours, which is what a pensum\'s `totalHours` means.\n\n**Hours may carry a half**, in either `4.5` or the Spanish `4,5` — the latter has to be\nquoted, or it is not one cell but two. Any other fraction is refused against its row.\n\nColumn reference and a template: `docs/templates/`.':
    'Requiere `ROLE_ADMIN`. Carga pensums completos desde una hoja de cálculo: el respaldo del catálogo, para cuando no se puede llegar a SINU. Es la única escritura que recibe el catálogo; por lo demás, los programas y los pensums son de SINU.\n\nUna fila por ítem del pensum. Las columnas antes de `pensumItemCode` describen el pensum y se repiten en cada una de sus filas; la importación revisa que esos valores repetidos coincidan entre sí en lugar de dejar que la primera fila gane en silencio. Un archivo puede traer varios pensums.\n\n**No se escribe nada a menos que todo el archivo sea válido.** Una importación parcial dejaría el catálogo en un estado que nadie eligió, así que primero se reúnen todos los problemas y se reportan con el número de línea del archivo.\n\n**Los totales declarados se revisan, no se creen.** La importación suma las materias y las compara con `declaredCredits` y `declaredHours`; este último es la suma de horas *semanales*, que es lo que quiere decir el `totalHours` de un pensum.\n\n**Las horas pueden llevar media hora**, como `4.5` o como el `4,5` en español; este último tiene que ir entre comillas, o no es una celda sino dos. Cualquier otra fracción se rechaza en su fila.\n\nReferencia de columnas y una plantilla: `docs/templates/`.',
  'The authenticated student\'s semáforo, read from SINU: their program, pensum and level,\nand one entry per item of that pensum with its status, period and, when SINU gives it,\nits grade. Nothing here is stored by KApp or writable through it.\n\nItems SINU has no record of come back `PENDING`, so the list always mirrors the pensum\none to one.\n\n`404` when SINU has no program for the caller — someone who is not a student, or not yet\none.':
    'El semáforo del estudiante autenticado, leído de SINU: su programa, su pensum y su nivel, y una entrada por cada ítem de ese pensum con su estado, su periodo y, cuando SINU la entrega, su nota. Nada de esto lo guarda KApp ni se puede escribir a través de él.\n\nLos ítems de los que SINU no tiene registro vuelven como `PENDING`, así que la lista siempre refleja el pensum uno a uno.\n\n`404` cuando SINU no tiene programa para quien llama: alguien que no es estudiante, o que todavía no lo es.',
  'A student\'s semáforo as SINU has it: one entry per item of their pensum.':
    'El semáforo de un estudiante tal como lo tiene SINU: una entrada por cada ítem de su pensum.',
  'Identifier of the KApp user, taken from the JWT `sub` claim. An opaque string, never parsed as a number.':
    'Identificador del usuario de KApp, tomado del claim `sub` del JWT. Una cadena opaca, que nunca se interpreta como número.',
  'The pensum SINU has the student on.':
    'El pensum en el que SINU tiene al estudiante.',
  'The level (semester) SINU has the student in.':
    'El nivel (semestre) en el que SINU tiene al estudiante.',
  'Where this came from. `TEST` is the adapter that serves invented data while the\nuniversity has not opened SINU; a client shows that the data is not real.':
    'De dónde salió esto. `TEST` es el adaptador que sirve datos inventados mientras la universidad no ha abierto SINU; un cliente muestra que los datos no son reales.',
  'When this was read from SINU. KApp keeps it for a few minutes at most.':
    'Cuándo se leyó de SINU. KApp lo guarda unos pocos minutos como mucho.',
  'One entry per item of the pensum, in the pensum\'s order. 51 entries for the Ingeniería\nde Sistemas plan; the example in this document shows a representative slice.':
    'Una entrada por cada ítem del pensum, en el orden del pensum. 51 entradas para el plan de Ingeniería de Sistemas; el ejemplo de este documento muestra una muestra representativa.',
  'One item of a student\'s semáforo: the status SINU gives a single pensum item, plus the\ncourse that filled it when the item is an elective slot.':
    'Un ítem del semáforo de un estudiante: el estado que SINU le da a un solo ítem del pensum, más la materia que lo llenó cuando el ítem es un cupo de electiva.',
  'The pensum item this entry is about.':
    'El ítem del pensum del que trata esta entrada.',
  'The status of one item on a student\'s semáforo, normalised from SINU\'s.\n\n- `PASSED` — *aprobada*.\n- `IN_PROGRESS` — being taken this period.\n- `PENDING` — not taken yet.\n- `FAILED` — *perdida*: the last attempt was not passed.\n- `POSTPONED` — *aplazada*. How SINU uses it is to be confirmed with the university;\n  until then it counts like a course not passed.\n\n*Blocked* is **not** a status: it is derived from prerequisites, and is the complement of\n`GET /api/semaphore/me/eligible` within the `PENDING` set.':
    'El estado de un ítem en el semáforo de un estudiante, normalizado a partir del de SINU.\n\n- `PASSED`: *aprobada*.\n- `IN_PROGRESS`: se está cursando este periodo.\n- `PENDING`: todavía no se ha tomado.\n- `FAILED`: *perdida*: el último intento no se aprobó.\n- `POSTPONED`: *aplazada*. Cómo la usa SINU está por confirmar con la universidad; mientras tanto cuenta como una materia no aprobada.\n\n*Bloqueada* **no** es un estado: se deriva de los prerrequisitos, y es el complemento de `GET /api/semaphore/me/eligible` dentro del conjunto `PENDING`.',
  'The status exactly as SINU writes it, for a client to show when `status` alone does\nnot say enough, or when SINU uses one this API does not know yet. `null` for an item\nSINU has no record of.':
    'El estado exactamente como lo escribe SINU, para que un cliente lo muestre cuando `status` solo no dice lo suficiente, o cuando SINU usa uno que esta API todavía no conoce. `null` para un ítem del que SINU no tiene registro.',
  'Academic period in the university\'s own format: four-digit year followed by the semester,\n`1` or `2`. `"20262"` is the second semester of 2026. `null` when the item has not been\ntaken yet.':
    'Periodo académico en el formato de la propia universidad: el año en cuatro dígitos seguido del semestre, `1` o `2`. `"20262"` es el segundo semestre de 2026. `null` cuando el ítem todavía no se ha tomado.',
  'Final mark on the university\'s `0..50` integer scale, as SINU records it. Optional and\nread-only: `null` while the course has not settled, and whenever SINU does not provide\ngrades.':
    'Nota final en la escala entera `0..50` de la universidad, tal como la registra SINU. Opcional y de solo lectura: `null` mientras la materia no se ha cerrado, y siempre que SINU no entregue notas.',
  'For an elective slot, the code of the course that filled it, as SINU records it.\n`null` for a fixed course, and for a slot SINU has not filled.':
    'Para un cupo de electiva, el código de la materia que lo llenó, tal como lo registra SINU. `null` para una materia fija, y para un cupo que SINU no ha llenado.',
  'Name of the course that filled the elective slot, so a client can draw the grid\nwithout another lookup. `null` whenever `resolvedSinuCode` is.':
    'Nombre de la materia que llenó el cupo de electiva, para que un cliente pueda dibujar la grilla sin otra consulta. `null` siempre que `resolvedSinuCode` lo sea.',
  'Credit totals derived from the caller\'s semáforo. **Never stored** — recomputed on every\ncall, so it cannot drift from the grid the student is looking at.\n\nCounting rules:\n\n- `creditsPassed` counts items with status `PASSED`.\n- `creditsInProgress` counts items with status `IN_PROGRESS`.\n- `creditsRemaining` is `totalCredits - creditsPassed - creditsInProgress`; `PENDING`,\n  `FAILED` and `POSTPONED` items therefore all count as remaining.\n- `byArea` carries one row per area declared by the pensum, in the pensum\'s order.':
    'Totales de créditos derivados del semáforo de quien llama. **Nunca se guardan**: se recalculan en cada llamada, así que no pueden separarse de la grilla que está viendo el estudiante.\n\nReglas de conteo:\n\n- `creditsPassed` cuenta los ítems con estado `PASSED`.\n- `creditsInProgress` cuenta los ítems con estado `IN_PROGRESS`.\n- `creditsRemaining` es `totalCredits - creditsPassed - creditsInProgress`; por lo tanto los ítems `PENDING`, `FAILED` y `POSTPONED` cuentan todos como restantes.\n- `byArea` trae una fila por cada área que declara el pensum, en el orden del pensum.',
  'Total credits of the student\'s pensum.':
    'Créditos totales del pensum del estudiante.',
  'The semester SINU has the student in.':
    'El semestre en el que SINU tiene al estudiante.',
  'Returns the pensum items the student may take next: those whose prerequisites are\n**all** `PASSED` and whose own status is still `PENDING`. This is the computation the\n"traffic light" is named for — everything else `PENDING` is *blocked*, and a client can\npaint it as such by subtracting this list from the `PENDING` set.\n\nNotes:\n\n- Items with no prerequisites are eligible as soon as they are `PENDING`.\n- `FAILED` and `POSTPONED` items are **not** returned. They are taken again rather than\n  for the first time, and their status already says so.\n- Elective slots are returned like any other item.\n- Items are returned in pensum form (`PensumCourse`), not progress form, so the\n  client has credits, hours and area available without a second lookup.\n- Taking a course is still done in SINU. This only says what the prerequisites allow.':
    'Devuelve los ítems del pensum que el estudiante puede tomar a continuación: aquellos cuyos prerrequisitos están **todos** en `PASSED` y cuyo propio estado sigue siendo `PENDING`. Es el cálculo que le da nombre al "semáforo": todo lo demás en `PENDING` está *bloqueado*, y un cliente puede pintarlo así restando esta lista del conjunto `PENDING`.\n\nNotas:\n\n- Los ítems sin prerrequisitos son elegibles en cuanto están en `PENDING`.\n- Los ítems `FAILED` y `POSTPONED` **no** se devuelven. Se vuelven a tomar en lugar de tomarse por primera vez, y su estado ya lo dice.\n- Los cupos de electiva se devuelven como cualquier otro ítem.\n- Los ítems se devuelven en forma de pensum (`PensumCourse`), no de progreso, así que el cliente tiene a mano los créditos, las horas y el área sin una segunda consulta.\n- Tomar una materia se sigue haciendo en SINU. Esto solo dice lo que permiten los prerrequisitos.',
  'One course pinned to a level other than the pensum\'s, or an elective slot with a chosen course.':
    'Una materia fijada en un nivel distinto al del pensum, o un cupo de electiva con una materia elegida.',
  'The item that moved, by its `pensumItemCode`.':
    'El ítem que se movió, por su `pensumItemCode`.',
  'For an elective slot, the course from the elective bank the student means to take in\nit. `null` for a fixed course, or for a slot with no course chosen.':
    'Para un cupo de electiva, la materia del banco de electivas que el estudiante piensa tomar en él. `null` para una materia fija, o para un cupo sin materia elegida.',
  'Whether `electiveSinuCode` is in the elective bank of the current period, checked when\nthe plan is read. `false` means the course is not offered this semester and the client\nshould say so. `null` when there is no `electiveSinuCode`.':
    'Si `electiveSinuCode` está en el banco de electivas del periodo actual, revisado cuando se lee el plan. `false` quiere decir que la materia no se ofrece este semestre y el cliente debería decirlo. `null` cuando no hay `electiveSinuCode`.',
  'Requires `ROLE_STUDENT`. Idempotent: moving a course already at that level returns the\nplan unchanged.\n\n**Moving a course does not make it eligible.** `GET /api/semaphore/me/eligible` is\ncomputed from prerequisites actually approved, and it ignores plans entirely. Planning\nis not approving, and a plan that could unlock a course by dragging it would be a\nsemáforo that lies.\n\nThere is **no cap on how many courses may sit at one level**. Students take more or\nfewer than the nominal six all the time, and a limit here would refuse a real timetable.\n\nFor an elective slot, `electiveSinuCode` names the course from the elective bank the\nstudent means to take in it. It is a plan, not an enrolment: the bank is checked when the\nplan is read, not when it is saved, because it changes every semester.\n\n`400` when the item is not in the pensum, when `plannedLevel` is outside 1–12, or when\n`electiveSinuCode` is given for an item that is not an elective slot.':
    'Requiere `ROLE_STUDENT`. Idempotente: mover una materia que ya está en ese nivel devuelve el plan sin cambios.\n\n**Mover una materia no la vuelve elegible.** `GET /api/semaphore/me/eligible` se calcula con los prerrequisitos aprobados de verdad, e ignora los planes por completo. Planear no es aprobar, y un plan que pudiera desbloquear una materia con solo arrastrarla sería un semáforo que miente.\n\n**No hay tope de cuántas materias puede haber en un nivel.** Los estudiantes toman más o menos de las seis nominales todo el tiempo, y un límite aquí rechazaría un horario real.\n\nPara un cupo de electiva, `electiveSinuCode` nombra la materia del banco de electivas que el estudiante piensa tomar en él. Es un plan, no una inscripción: el banco se revisa cuando se lee el plan, no cuando se guarda, porque cambia cada semestre.\n\n`400` cuando el ítem no está en el pensum, cuando `plannedLevel` está fuera de 1–12, o cuando se envía `electiveSinuCode` para un ítem que no es un cupo de electiva.',
  'The item\'s `pensumItemCode`, for a fixed course or an elective slot alike.':
    'El `pensumItemCode` del ítem, para una materia fija o un cupo de electiva por igual.',
  'Requires `ROLE_STUDENT`. Removes the placement, so the course goes back to its published\nlevel and an elective slot to no chosen course. `404` when the plan has no placement for\nthat item — it was never moved.':
    'Requiere `ROLE_STUDENT`. Quita la ubicación, así que la materia vuelve a su nivel publicado y un cupo de electiva queda sin materia elegida. `404` cuando el plan no tiene ubicación para ese ítem: nunca se movió.',
  // schedule.openapi.yaml, contracts 2.0 (October 2026)
  'Returns the caller\'s timetable as SINU has it, with every section, meeting and meeting\nperiod nested inside it. When `period` is omitted the current period is returned.':
    'Devuelve el horario de quien llama tal como lo tiene SINU, con cada sección, reunión y periodo de reunión anidados. Cuando se omite `period`, devuelve el periodo actual.',
  'Academic period to read. When omitted, the current period is returned.':
    'Periodo académico que se quiere leer. Cuando se omite, se devuelve el periodo actual.',
  'A person\'s whole timetable for one academic period, as SINU has it.':
    'El horario completo de una persona en un periodo académico, tal como lo tiene SINU.',
  'Code of the student\'s academic program. `null` on a professor\'s timetable.':
    'Código del programa académico del estudiante. `null` en el horario de un profesor.',
  'The study plan (pensum) the student follows. `null` on a professor\'s timetable.':
    'El plan de estudios (pensum) que sigue el estudiante. `null` en el horario de un profesor.',
  'The semester the student is in during this period. `null` on a professor\'s timetable.':
    'El semestre que cursa el estudiante en este periodo. `null` en el horario de un profesor.',
  'Whether this is the current period: the one served when `period` is omitted.':
    'Si este es el periodo actual: el que se sirve cuando se omite `period`.',
  'Where this came from. `TEST` is the adapter that serves invented timetables while the\nuniversity has not opened SINU; a client shows that the data is not real.':
    'De dónde salió esto. `TEST` es el adaptador que sirve horarios inventados mientras la universidad no ha abierto SINU; un cliente muestra que los datos no son reales.',
  'The courses the person takes, or teaches, during this period.':
    'Las materias que la persona toma, o dicta, en este periodo.',
  'One course the person takes, or teaches, during the period, in one group, with its weekly\nmeetings — what SINU\'s report lists as an *asignatura* with its *grupo*.':
    'Una materia que la persona toma, o dicta, en el periodo, en un grupo, con sus reuniones semanales: lo que el reporte de SINU lista como una *asignatura* con su *grupo*.',
  'SINU\'s identifier of this course and group in the period — the report\'s `Cod.`.':
    'El identificador que SINU le da a esta materia y grupo en el periodo: el `Cod.` del reporte.',
  'The course code as SINU carries it. The same `sinuCode` the semáforo shows, so a client\ncan tell which item of the student\'s semáforo this class is.':
    'El código de la materia tal como lo tiene SINU. El mismo `sinuCode` que muestra el semáforo, para que un cliente sepa qué ítem del semáforo del estudiante es esta clase.',
  'The matching item of the student\'s pensum, as the semáforo addresses it. `null` for a\ncourse outside the student\'s pensum, and always for a professor\'s timetable.':
    'El ítem correspondiente del pensum del estudiante, como lo identifica el semáforo. `null` para una materia que está fuera del pensum del estudiante, y siempre en el horario de un profesor.',
  'The course name, as SINU writes it.':
    'El nombre de la materia, como lo escribe SINU.',
  'Academic credits.':
    'Créditos académicos.',
  'Total hours of the course over the semester, as SINU prints them. A SINU hour is a\n45-minute block: weekly blocks times 16.':
    'Horas totales de la materia en el semestre, como las imprime SINU. Una hora de SINU es un bloque de 45 minutos: los bloques semanales por 16.',
  'The group code.':
    'El código del grupo.',
  'The professor\'s full name, in SINU\'s own uppercase surname-first form. On a professor\'s\nown timetable, their own name.':
    'El nombre completo del profesor, en la forma de SINU: en mayúsculas y primero los apellidos. En el horario de un profesor, su propio nombre.',
  'The sede as SINU writes it. In SINU each building is a sede.':
    'La sede como la escribe SINU. En SINU cada edificio es una sede.',
  'The KApp building that sede maps to, for the map. `null` when nobody has mapped that\nsede to a building yet.':
    'El edificio de KApp que corresponde a esa sede, para el mapa. `null` cuando nadie ha asignado todavía esa sede a un edificio.',
  'First day of the course over the whole semester, inclusive. Equal to the earliest\n`from` across all of this section\'s meeting periods.':
    'Primer día de la materia en todo el semestre, inclusive. Igual al `from` más temprano de todos los periodos de reunión de esta sección.',
  'Last day of the course over the whole semester, inclusive. Equal to the latest `to`\nacross all of this section\'s meeting periods.':
    'Último día de la materia en todo el semestre, inclusive. Igual al `to` más tardío de todos los periodos de reunión de esta sección.',
  'The colour the client paints this course with, as `#RRGGBB`. KApp assigns it from the\nsix non-pink colours of the palette. Each course starts from a colour of its own, which\nit keeps from one period to the next, and two courses of one timetable never share one\nwhile there are colours left.':
    'El color con el que el cliente pinta esta materia, como `#RRGGBB`. KApp lo asigna entre los seis colores no rosados de la paleta. Cada materia parte de un color propio, que conserva de un periodo al siguiente, y dos materias de un mismo horario nunca comparten uno mientras queden colores.',
  'The weekly slots this course is taught in.':
    'Las franjas semanales en las que se dicta esta materia.',
  'One weekly day + time slot of a section, together with the date ranges over which it is\nactually taught and the room in force during each of them.':
    'Una franja semanal de día y hora de una sección, junto con los rangos de fechas en los que de verdad se dicta y el salón vigente en cada uno.',
  'When the class ends. Always later than `startTime`.':
    'Cuándo termina la clase. Siempre después de `startTime`.',
  'How many 45-minute blocks the class lasts, which is how SINU schedules it.':
    'Cuántos bloques de 45 minutos dura la clase, que es como la programa SINU.',
  'The disjoint date ranges over which this weekly slot is taught, in ascending order of\n`from`.':
    'Los rangos de fechas disjuntos en los que se dicta esta franja semanal, en orden ascendente de `from`.',
  'One contiguous stretch of the semester during which a weekly meeting is taught in a given\nroom. Both endpoints are **inclusive**, and a single-day range has `from` equal to `to`.\n\nA meeting\'s periods are disjoint and never overlap each other; the gaps between them are\nthe weeks the class does not meet.':
    'Un tramo continuo del semestre durante el que una reunión semanal se dicta en un salón dado. Los dos extremos son **inclusivos**, y un rango de un solo día tiene `from` igual a `to`.\n\nLos periodos de una reunión son disjuntos y nunca se superponen; los huecos entre ellos son las semanas en que la clase no se reúne.',
  'The room, as SINU names it — usually a number whose first digit is the floor: `"302"` is on\nthe third floor. It identifies a room only together with the section\'s building: the same\nnumber exists in more than one building.\n\n`null` means no classroom has been assigned for that stretch of the semester. It is a real,\ncommon state in SINU, not a missing value, and clients must render it.':
    'El salón, como lo nombra SINU: normalmente un número cuyo primer dígito es el piso; `"302"` está en el tercer piso. Solo identifica un salón junto con el edificio de la sección: el mismo número existe en más de un edificio.\n\n`null` quiere decir que no hay salón asignado para ese tramo del semestre. Es un estado real y común en SINU, no un valor faltante, y los clientes deben mostrarlo.',
  'Returns one entry per period SINU has a timetable for, newest period first. Clients use it\nto populate the period switcher without downloading every schedule.':
    'Devuelve una entrada por cada periodo del que SINU tiene horario, primero el más reciente. Los clientes lo usan para llenar el selector de periodo sin descargar todos los horarios.',
  'Whether this is the current period.':
    'Si este es el periodo actual.',
  'How many courses the timetable for that period holds.':
    'Cuántas materias tiene el horario de ese periodo.',
  'Resolves which meetings actually take place on `date`. For every meeting in the caller\'s\nschedule the server checks the meeting\'s `dayOfWeek` against the weekday of `date`, then\nwalks the meeting\'s `periods[]` for a range that contains `date` (both endpoints inclusive).\nMatching meetings are returned with the room in force during that range, which may be\n`null`.\n\nThe result is sorted by `startTime` ascending. An empty array means no classes that day.\n\n**This is the endpoint the mobile home screen calls.**':
    'Resuelve qué reuniones ocurren de verdad en `date`. Para cada reunión del horario de quien llama, el servidor compara el `dayOfWeek` de la reunión con el día de la semana de `date`, y luego recorre los `periods[]` de la reunión buscando un rango que contenga `date` (ambos extremos inclusivos). Las reuniones que coinciden se devuelven con el salón vigente en ese rango, que puede ser `null`.\n\nEl resultado va ordenado por `startTime` ascendente. Un arreglo vacío quiere decir que ese día no hay clases.\n\n**Este es el endpoint que llama la pantalla de inicio de la app.**',
  'The section this class belongs to, for drilling into the course detail.':
    'La sección a la que pertenece esta clase, para entrar al detalle de la materia.',
  'How many 45-minute blocks the class lasts.':
    'Cuántos bloques de 45 minutos dura la clase.',
  'The KApp building of the sede, or `null` while the sede is not mapped.':
    'El edificio de KApp que corresponde a la sede, o `null` mientras la sede no esté asignada.',
  'RGB hex the client draws this class in, assigned by KApp so a week is readable at a glance.':
    'Color RGB en hexadecimal con el que el cliente dibuja esta clase, asignado por KApp para que una semana se lea de un vistazo.',
  'Resolves a whole week at once, using exactly the same rule as\n`GET /api/schedule/me/day` applied to each of the seven dates.\n\n`date` may be **any** date inside the wanted week; the server snaps it to the Monday of that\nweek. Weeks run Monday to Sunday. When `date` is omitted the current week is returned.\n\nAll seven day keys are always present; a day with no classes carries an empty array. Each\nday\'s classes are sorted by `startTime` ascending. `weekStart` and `weekEnd` tell the client\nwhich calendar dates the seven buckets correspond to, so day, 2-day and week views can all\nbe rendered from a single call.':
    'Resuelve una semana completa de una vez, con exactamente la misma regla de `GET /api/schedule/me/day` aplicada a cada una de las siete fechas.\n\n`date` puede ser **cualquier** fecha dentro de la semana que se quiere; el servidor la lleva al lunes de esa semana. Las semanas van de lunes a domingo. Cuando se omite `date`, se devuelve la semana actual.\n\nLas siete claves de día siempre están presentes; un día sin clases trae un arreglo vacío. Las clases de cada día van ordenadas por `startTime` ascendente. `weekStart` y `weekEnd` le dicen al cliente a qué fechas del calendario corresponden los siete grupos, así que las vistas de día, de 2 días y de semana se pueden dibujar con una sola llamada.',
  // map.openapi.yaml, contracts 2.0 (October 2026)
  'Every building with its wings and floors, ordered by code. `q` matches the code, the name\nand every alias, ignoring case and accents.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.':
    'Todos los edificios con sus alas y pisos, ordenados por código. `q` coincide con el código, el nombre y cada alias, sin distinguir mayúsculas ni tildes.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.',
  'The `ETag` of the answer the client already has. When nothing changed since, the answer is `304` with no body. Since 3.6.':
    'El `ETag` de la respuesta que el cliente ya tiene. Si nada cambió desde entonces, la respuesta es `304`, sin cuerpo. Desde la 3.6.',
  'The names SINU gives this building as a sede, exactly as its timetable prints them. Empty until somebody maps them in the portal. Since 3.6.':
    'Los nombres que SINU le da a este edificio como sede, exactamente como los imprime su horario. Vacío hasta que alguien los asigna en el portal. Desde la 3.6.',
  'Allowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.':
    'Roles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.',
  'The call that draws a floor: a drawing `width` x `height` units, origin at the top left,\nwith the building\'s `outline`, each space as the polygon in its `shape` with its `doors`\non it, and the corridors along their paths. Spaces without `shape` are on the floor but not\ndrawn yet - list them beside the drawing rather than dropping them. The building\'s wings\ncome along so each space\'s `wing` code can be shown by name.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.':
    'La llamada que dibuja un piso: un dibujo de `width` x `height` unidades, con el origen arriba a la izquierda, con el `outline` del edificio, cada espacio como el polígono de su `shape` con sus `doors` encima, y los pasillos a lo largo de sus recorridos. Los espacios sin `shape` están en el piso pero todavía no se han dibujado: lístelos junto al dibujo en lugar de descartarlos. Las alas del edificio vienen incluidas para que el código `wing` de cada espacio se pueda mostrar por su nombre.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.',
  '**With `q`:** matched against the door code, the name and the aliases, case- and\naccent-insensitively, ordered by descending relevance. It matches whole words, not\nprefixes: `sistemas` finds the lab, `sistem` does not. Internal codes are never matched,\nso a generated code cannot surface through the search.\n\n**Without `q`:** every space the filters allow, ordered by building, floor and code -\nhow a floor is managed rather than how a person finds a room.\n\n`type` takes one type code; `category` takes a whole category. Unplaced spaces are\nincluded.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.':
    '**Con `q`:** coincide con el código de puerta, el nombre y los alias, sin distinguir mayúsculas ni tildes, ordenado por relevancia descendente. Coincide con palabras completas, no con prefijos: `sistemas` encuentra el laboratorio, `sistem` no. Los códigos internos nunca coinciden, así que un código generado no puede aparecer en la búsqueda.\n\n**Sin `q`:** todos los espacios que permiten los filtros, ordenados por edificio, piso y código: así se administra un piso, no así busca una persona un salón.\n\n`type` recibe un código de tipo; `category` recibe una categoría completa. Se incluyen los espacios sin ubicar.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.',
  'The endpoint the schedule screen calls when a student taps a class: one round trip from\nthe code to the space, its floor and a summary of its building.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.':
    'El endpoint que llama la pantalla del horario cuando un estudiante toca una clase: un solo viaje del código al espacio, su piso y un resumen de su edificio.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.',
  'The names SINU gives this building as a sede. Since 3.6.':
    'Los nombres que SINU le da a este edificio como sede. Desde la 3.6.',
  'The whole catalogue, grouped by category in the category\'s declared order, then by name.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.':
    'El catálogo completo, agrupado por categoría en el orden declarado de las categorías, y luego por nombre.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.',
  'Every campus that has at least one building, with its building count.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.':
    'Todas las sedes que tienen al menos un edificio, con su número de edificios.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.',
  'The city around a campus - its blocks, sidewalks, roadways, medians and named streets, and\nthe lots of the blocks the university\'s buildings stand on - from the city\'s reference\nmap, for drawing under the buildings. Coordinates are\n`[lon, lat]` in WGS 84; lay a building\'s drawing on them with its `placement`. The campus\nname matches ignoring case and accents. `404` for a campus with no ground yet.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.':
    'La ciudad alrededor de una sede (sus manzanas, andenes, calzadas, separadores y calles con nombre, y los lotes de las manzanas donde están los edificios de la universidad) tomada del mapa de referencia de la ciudad, para dibujar debajo de los edificios. Las coordenadas son `[lon, lat]` en WGS 84; ubique encima el dibujo de un edificio con su `placement`. El nombre de la sede coincide sin distinguir mayúsculas ni tildes. `404` para una sede que todavía no tiene terreno.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.',
  'Everything on the campus\'s blocks that is not the university\'s - a neighbour\'s building,\na heritage house with its garden - for drawing around the buildings. An empty list, at\nversion `0`, while nothing has been saved. The campus name matches ignoring case and\naccents.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.':
    'Todo lo que hay en las manzanas de la sede que no es de la universidad (el edificio de un vecino, una casa patrimonial con su jardín), para dibujarlo alrededor de los edificios. Una lista vacía, en la versión `0`, mientras no se haya guardado nada. El nombre de la sede coincide sin distinguir mayúsculas ni tildes.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.',
  'Where each wall stands from the curb and how long it is, as taken on site with the portal\'s\nsurvey sheet. None, at version `0`, while nothing has been saved. The campus name matches\nignoring case and accents.\n\nAllowed roles: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.':
    'Dónde queda cada muro respecto al sardinel y cuánto mide, tal como se tomó en el sitio con la hoja de levantamiento del portal. Ninguna, en la versión `0`, mientras no se haya guardado nada. El nombre de la sede coincide sin distinguir mayúsculas ni tildes.\n\nRoles permitidos: `ROLE_GUEST`, `ROLE_STUDENT`, `ROLE_PROFESSOR`, `ROLE_STAFF`, `ROLE_ADMIN`.',
};
