import { loginHandler } from '../_shared/auth.mjs';
Deno.serve(loginHandler((name: string) => Deno.env.get(name)));
