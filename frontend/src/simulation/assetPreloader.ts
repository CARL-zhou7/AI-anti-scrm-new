/** Loads and decodes portrait images before the start button appears (resource loading is not timed). */
export async function preloadImages(urls: string[]): Promise<void> {
  await Promise.all([...new Set(urls)].map(url => new Promise<void>((resolve, reject) => {
    const img = new Image()
    img.onload = () => { img.decode ? img.decode().then(() => resolve(), () => resolve()) : resolve() }
    img.onerror = () => reject(new Error('image failed: ' + url))
    img.src = url
  })))
}
