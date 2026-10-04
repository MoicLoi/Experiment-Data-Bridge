import { describe, expect, it } from 'vitest'
import { resolvePhrasePattern, validatePhraseParameters } from './WorkflowPhraseComposer'

describe('phrase parameter schema validation', () => {
  it('applies allOf and accepts one matching anyOf branch', () => {
    const errors = validatePhraseParameters({
      type: 'object',
      allOf: [{ required: ['name'] }],
      anyOf: [
        { properties: { value: { type: 'number' } }, required: ['value'] },
        { properties: { label: { type: 'string' } }, required: ['label'] }
      ]
    }, { name: 'sample', value: 3 })
    expect(errors).toEqual([])
  })

  it('requires exactly one oneOf branch', () => {
    const schema = {
      type: 'object' as const,
      oneOf: [
        { properties: { mode: { enum: ['a'] } }, required: ['mode'] },
        { properties: { mode: { enum: ['b'] } }, required: ['mode'] }
      ]
    }
    expect(validatePhraseParameters(schema, { mode: 'a' })).toEqual([])
    expect(validatePhraseParameters(schema, { mode: 'c' })).toContain('parameters: must match exactly one allowed schema')
  })
})

describe('phrase pattern v1 compatibility', () => {
  const item = {
    type: 'datafile.convert', version: '1', source: 'BUILT_IN', title: 'Convert', schemaAvailable: true, capabilities: [],
    phrasePatterns: [
      { id: 'convert-short', stepType: 'datafile.convert', stepVersion: '1', tokens: [] },
      { id: 'convert-natural', stepType: 'datafile.convert', stepVersion: '1', tokens: [] }
    ]
  }

  it('maps a v1 action without patternId to the first catalog pattern', () => {
    expect(resolvePhrasePattern(item)).toEqual(item.phrasePatterns[0])
  })

  it('keeps a stable selected pattern id', () => {
    expect(resolvePhrasePattern(item, 'convert-natural')).toEqual(item.phrasePatterns[1])
  })
})
