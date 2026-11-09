/**
 * Additive Free V2 rollout guard. Source-only: never deployed from CI.
 *
 * This code is deliberately independent from Supabase, Deno.env and HTTP.
 * Only the server operator can set BROTHER_FREE_V2_ENABLED=true. A
 * publishable key, signed-in client, or QA flag NEVER enables this route.
 */
import {
  reserveImportV2,
  type IdempotentReserveInput,
  type ReserveV2Rpc,
} from "./reserve-v2.ts";

export type FreeV2Rollout = {
  enabled: boolean;
  reserve: ReserveV2Rpc;
};

export async function handleFreeV2Reserve(
  input: IdempotentReserveInput,
  rollout: FreeV2Rollout,
): Promise<Awaited<ReturnType<typeof reserveImportV2>>> {
  // Fail closed before touching even a fake database.
  if (rollout.enabled !== true) {
    return {
      status: 503,
      body: { message: "Reserva Free V2 indisponível até homologação." },
    };
  }
  return await reserveImportV2(input, rollout.reserve);
}
