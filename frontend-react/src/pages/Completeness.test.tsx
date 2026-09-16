import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Completeness } from './Completeness'
import { renderOffline } from '../test/render'

describe('Completeness', () => {
  it('lists every framework with its controls-evaluated ratio and avg completeness (mock, all applications)', async () => {
    renderOffline(<Completeness />)

    // the shared fixtures' catalog has 4 frameworks: PCI_DSS (2 controls, 1 evaluated),
    // C-SITE (3, all evaluated), ITPP (1, evaluated), DPSC (1, evaluated).
    expect(await screen.findByText('Frameworks — 4')).toBeInTheDocument()

    const pciRow = screen.getByText('PCI_DSS', { selector: 'td' }).closest('tr') as HTMLElement
    expect(within(pciRow).getByText('1 / 2')).toBeInTheDocument()
    expect(within(pciRow).getByText('PARTIAL')).toBeInTheDocument()

    const cSiteRow = screen.getByText('C-SITE', { selector: 'td' }).closest('tr') as HTMLElement
    expect(within(cSiteRow).getByText('3 / 3')).toBeInTheDocument()
    expect(within(cSiteRow).getByText('EVALUATED')).toBeInTheDocument()

    expect(screen.getByText('Controls', { selector: '.stat-label' })).toBeInTheDocument()
    expect(screen.getByText('Avg completeness', { selector: '.stat-label' })).toBeInTheDocument()
  })

  it('expanding a framework shows its controls, including the not-evaluated one with no percentage', async () => {
    renderOffline(<Completeness />)
    await screen.findByText('Frameworks — 4')

    await userEvent.click(screen.getByText('PCI_DSS', { selector: 'td' }).closest('tr') as HTMLElement)

    expect(await screen.findByText('DB-TLS-IN-TRANSIT')).toBeInTheDocument()
    expect(screen.getByText('PCI-DSS-6.2')).toBeInTheDocument()
    const untouchedRow = screen.getByText('PCI-DSS-6.2').closest('tr') as HTMLElement
    expect(within(untouchedRow).getByText('—')).toBeInTheDocument()
    expect(within(untouchedRow).getByText('NOT_EVALUATED')).toBeInTheDocument()
  })

  it('clicking an evaluated control opens the evidence drill-down, and a row shows its factor breakdown', async () => {
    renderOffline(<Completeness />)
    await screen.findByText('Frameworks — 4')

    await userEvent.click(screen.getByText('PCI_DSS', { selector: 'td' }).closest('tr') as HTMLElement)
    await screen.findByText('DB-TLS-IN-TRANSIT')
    await userEvent.click(screen.getByText('DB-TLS-IN-TRANSIT').closest('tr') as HTMLElement)

    const dialog = await screen.findByRole('dialog', { name: /DB-TLS-IN-TRANSIT —/ })
    expect(within(dialog).getByText('net-banking')).toBeInTheDocument()

    await userEvent.click(within(dialog).getByText('net-banking').closest('tr') as HTMLElement)
    expect(within(dialog).getByText('Evidence breakdown', { exact: false })).toBeInTheDocument()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Close' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('filtering by application narrows the evaluated ratio without changing the framework list', async () => {
    renderOffline(<Completeness />)
    await screen.findByText('Frameworks — 4')

    await userEvent.selectOptions(screen.getByLabelText('Application'), 'net-banking')
    await waitFor(() => {
      const pciRow = screen.getByText('PCI_DSS', { selector: 'td' }).closest('tr') as HTMLElement
      expect(within(pciRow).getByText('1 / 2')).toBeInTheDocument()
    })
    expect(screen.getByText('Frameworks — 4')).toBeInTheDocument()
  })

  it('the Framework dropdown is populated from the frameworks rollup and narrows the framework table', async () => {
    renderOffline(<Completeness />)
    await screen.findByText('Frameworks — 4')

    const frameworkSelect = screen.getByLabelText('Framework') as HTMLSelectElement
    const optionValues = within(frameworkSelect)
      .getAllByRole('option')
      .map((o) => (o as HTMLOptionElement).value)
    expect(optionValues).toEqual(['', 'C-SITE', 'DPSC', 'ITPP', 'PCI_DSS'])

    await userEvent.selectOptions(frameworkSelect, 'PCI_DSS')
    await waitFor(() => expect(screen.getByText('Frameworks — 1')).toBeInTheDocument())
    expect(screen.getByText('PCI_DSS', { selector: 'td' })).toBeInTheDocument()
    expect(screen.queryByText('C-SITE', { selector: 'td' })).not.toBeInTheDocument()

    await userEvent.selectOptions(frameworkSelect, '')
    await waitFor(() => expect(screen.getByText('Frameworks — 4')).toBeInTheDocument())
  })
})
