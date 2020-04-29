import React from 'react';
import renderer from 'react-test-renderer';

import OTPublisher from '../src/OTPublisher';

jest.mock('../src/OT', () => ({
  OT: {
    setJSComponentEvents: jest.fn(),
    initPublisher: jest.fn(),
    destroyPublisher: jest.fn()
  },
  setNativeEvents: jest.fn() 
}));

describe('OTPublisher', () => {
  describe('no props', () => {
    it('should render an empty view', () => {
      const publisher = renderer.create(<OTPublisher />).toJSON();
      expect(publisher).toMatchSnapshot();
    });
  });

  describe('with props', () => {

  });
});