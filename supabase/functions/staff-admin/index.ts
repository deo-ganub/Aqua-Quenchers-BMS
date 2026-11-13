import { staffHandler } from '../_shared/auth.mjs';
Deno.serve(staffHandler((name: string) => Deno.env.get(name)));
