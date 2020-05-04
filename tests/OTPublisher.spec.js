import React from 'react';
import renderer from 'react-test-renderer';
import { mount } from 'enzyme';
import { OT } from '../src/OT';

import OTPublisher from '../src/OTPublisher';

jest.mock('../src/OT', () => ({
  OT: {
    setJSComponentEvents: jest.fn(),
    initPublisher: jest.fn(),
    destroyPublisher: jest.fn()
  },
  setNativeEvents: jest.fn() 
}));

// The publisher component will be rendering an empty View,
// so just ignore the warning.
console.error = jest.fn();

describe('OTPublisher', () => {
  describe('no props', () => {
    it('should render an empty view', () => {
      const publisher = renderer.create(<OTPublisher />).toJSON();
      expect(publisher).toMatchSnapshot();
    });
  });

  describe('with props', () => {
    let publisher = 'fakePublisher';
    let publisherId = 'fakePublisherId';
    let publisherComponent;
    let instance;

    beforeEach(() => {
      publisherComponent = mount(
        <OTPublisher publisher={publisher} publisherId={publisherId} />
      );

      instance = publisherComponent.instance();

    });

    describe('when component mounts', () => {
      beforeEach(() => {
        jest.spyOn(instance, 'createPublisher');
        jest.spyOn(instance, 'initPublisher');
        instance.componentDidMount();
      });

      it('should call createPublisher', () => {
        expect(instance.createPublisher).toHaveBeenCalledTimes(1);
      });

      it('should call initPublisher', () => {
        expect(instance.initPublisher).toHaveBeenCalledTimes(1);
      });

      it('should call OT.initPublisher', () => {
        expect(OT.initPublisher).toHaveBeenCalled();
      });
    });
  });
});