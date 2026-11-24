import { test, expect } from '@playwright/test';

test('login, ask a question, and show a cited source', async ({ page }) => {
  test.skip(!process.env.KPF_E2E_PASSWORD, 'Set KPF_E2E_PASSWORD for a running demo backend with synthetic users.');
  const username = process.env.KPF_E2E_USERNAME ?? 'demo-admin';
  const password = process.env.KPF_E2E_PASSWORD ?? '';

  await page.goto('/login');
  await page.getByLabel('Identifiant de démonstration').fill(username);
  await page.getByLabel('Mot de passe').fill(password);
  await page.getByRole('button', { name: 'Se connecter' }).click();
  await expect(page.getByRole('heading', { name: 'Questions-réponses' })).toBeVisible();

  const backendUrl = process.env.KPF_BACKEND_URL ?? 'http://127.0.0.1:8080';
  const accessToken = await page.evaluate(() => sessionStorage.getItem('kpf-token'));
  expect(accessToken).toBeTruthy();
  const documentBody = '# Règle de démonstration\n\nLa règle synthétique KPF-42 impose une revue avant toute modification du seuil de risque afin de protéger la cohérence du profil.\n';
  const uploaded = await page.request.post(`${backendUrl}/api/rag/documents`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    multipart: { file: { name: 'synthetic-rule.md', mimeType: 'text/markdown', buffer: Buffer.from(documentBody) } }
  });
  expect(uploaded.ok()).toBeTruthy();

  await page.getByLabel('Votre question').fill('Pourquoi la règle KPF-42 impose-t-elle une revue du seuil de risque ?');
  await page.getByRole('button', { name: 'Poser la question' }).click();
  await expect(page.getByRole('heading', { name: /S1.*synthetic-rule\.md/ })).toBeVisible({ timeout: 90_000 });
  await page.getByText("Afficher l'extrait exact").click();
  await expect(page.getByText(/cohérence du profil/)).toBeVisible();
  const localSourceLink = page.getByRole('link', { name: "Aller à l'extrait exact" });
  await expect(localSourceLink).toBeVisible();
  await localSourceLink.click();
  await expect(page).toHaveURL(/#source-S1$/);
});
