/**
 * Copies text to the clipboard, and says whether it did. The Clipboard API exists only on a secure
 * page - HTTPS or localhost - so on the portal opened over plain http by a tailnet address it is
 * missing; there, and wherever the browser refuses, it copies the old way, still inside the tap.
 */
export async function copyText(text: string): Promise<boolean> {
  if (window.isSecureContext && navigator.clipboard) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      return copyByHand(text);
    }
  }
  return copyByHand(text);
}

/** Copies through a hidden text box and the browser's copy command, as pages did before the Clipboard API. */
function copyByHand(text: string): boolean {
  const box = document.createElement('textarea');
  box.value = text;
  box.setAttribute('readonly', '');
  box.style.position = 'fixed';
  box.style.top = '0';
  box.style.opacity = '0';
  document.body.appendChild(box);
  box.select();
  box.setSelectionRange(0, text.length); // Safari on iOS selects nothing without it
  let done = false;
  try {
    done = typeof document.execCommand === 'function' && document.execCommand('copy');
  } catch {
    done = false;
  }
  box.remove();
  return done;
}
