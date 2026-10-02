# Unknown protocol logging

ConnectionRunnable now uses a boolean local to each connection to log only its
first unknown protocol warning. The first two consecutive unknown codes receive
the existing rejection response; the third closes the active socket immediately.
Recognized Myster codes reset the consecutive count, including acknowledgments,
registered sections, and STLS. The logging flag remains set across valid sections
and TLS upgrades.

Updated run() Javadoc. Implemented directly from the user's request without a
separate plan; no design changes or new tests were needed for this logging change.

Validation: `mvn -o -DskipTests compile` passed with existing compiler warnings.
