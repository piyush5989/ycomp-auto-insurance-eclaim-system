const WORKSHOP_REPAIR_FLASH_KEY = 'eclaims-workshop-repair-updated'

export const setWorkshopRepairFlash = () => {
  try {
    sessionStorage.setItem(WORKSHOP_REPAIR_FLASH_KEY, '1')
  } catch {
    /* ignore private mode / quota */
  }
}

export const consumeWorkshopRepairFlash = (): boolean => {
  try {
    if (sessionStorage.getItem(WORKSHOP_REPAIR_FLASH_KEY)) {
      sessionStorage.removeItem(WORKSHOP_REPAIR_FLASH_KEY)
      return true
    }
  } catch {
    /* ignore */
  }
  return false
}
